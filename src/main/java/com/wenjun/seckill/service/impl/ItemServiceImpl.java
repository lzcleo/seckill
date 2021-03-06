package com.wenjun.seckill.service.impl;

import com.wenjun.seckill.dao.ItemDOMapper;
import com.wenjun.seckill.dao.ItemStockDOMapper;
import com.wenjun.seckill.dao.StockLogDOMapper;
import com.wenjun.seckill.dataobject.ItemDO;
import com.wenjun.seckill.dataobject.ItemStockDO;
import com.wenjun.seckill.dataobject.StockLogDO;
import com.wenjun.seckill.enums.EmBusinessError;
import com.wenjun.seckill.error.BusinessException;
import com.wenjun.seckill.mq.MqProducer;
import com.wenjun.seckill.service.ItemService;
import com.wenjun.seckill.service.PromoService;
import com.wenjun.seckill.service.model.ItemModel;
import com.wenjun.seckill.service.model.PromoModel;
import com.wenjun.seckill.validator.ValidationResult;
import com.wenjun.seckill.validator.ValidatorImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author: wenjun
 * @Date: 2019/12/20 15:04
 */
@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private ItemDOMapper itemDOMapper;

    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Autowired
    private PromoService promoService;

    @Autowired
    private RedisTemplate<Object,Object> redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        //校验入参
        ValidationResult result = validator.validate(itemModel);
        if (result.isHasErrors()) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }
        //转化itemModel->dataobject
        ItemDO itemDO = convertItemDOFromModel(itemModel);
        //写入数据库
        itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());

        ItemStockDO itemStockDO = convertItemStockDOFromModel(itemModel);
        itemStockDOMapper.insertSelective(itemStockDO);
        //返回
        return itemModel;
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOList = itemDOMapper.listItem();
        List<ItemModel> itemModelList = itemDOList.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            PromoModel promoModel = promoService.getPromoByItemId(itemDO.getId());
            if (promoModel == null || promoModel.getStatus() == 3) {
                itemDO.setTitle(itemDO.getTitle() + "（暂无秒杀活动！）");
            }
            if (promoModel != null) {
                if (promoModel.getStatus() == 1) {
                    itemDO.setTitle(itemDO.getTitle() + "（秒杀即将开始！）");
                } else if (promoModel.getStatus() == 2) {
                    itemDO.setTitle(itemDO.getTitle() + "（秒杀进行中！）");
                }
                itemDO.setPrice(promoModel.getPromoPrice().doubleValue());
            }
            ItemModel itemModel = this.convertModelFromDataObject(itemDO,itemStockDO);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {
        //获得商品信息
        ItemDO itemDo = itemDOMapper.selectByPrimaryKey(id);
        if (itemDo == null) {
            return null;
        }
        //获得库存信息
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(id);
        //dataobject -> model
        ItemModel itemModel = convertModelFromDataObject(itemDo,itemStockDO);
        //获得活动商品信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        if (promoModel != null && promoModel.getStatus() != 3) {
            itemModel.setPromoModel(promoModel);
        }
        return itemModel;
    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) {
        //影响的记录条数
        int affectedRow = itemStockDOMapper.decreaseStock(itemId,amount);
        if (affectedRow > 0) {
            //更新库存成功
            return true;
        } else {
            //更新库存失败
            return false;
        }
    }

    @Override
    @Transactional
    public boolean decreaseStockInRedis(Integer itemId, Integer amount) {
        long result = redisTemplate.opsForValue().decrement("promo_item_stock_" + itemId,amount);
        if (result > 0) {
            //更新库存成功
//            boolean mqResult = mqProducer.asyncReduceStock(itemId,amount);
//            if (!mqResult) {
//                redisTemplate.opsForValue().increment("promo_item_stock_" + itemId,amount);
//                return false;
//            }
            return true;
        } else if (result == 0) {
            //打上库存已售罄的标识
            redisTemplate.opsForValue().set("promo_item_stock_invalid_" + itemId,"true");
            //更新库存成功
            return true;
        } else {
            //更新库存失败（amount数量过多）
            increaseStockInRedis(itemId,amount);
            return false;
        }
    }

    @Override
    public boolean increaseStockInRedis(Integer itemId, Integer amount) {
        redisTemplate.opsForValue().increment("promo_item_stock_" + itemId,amount);
        return true;
    }

    @Override
    public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
        boolean mqResult = mqProducer.asyncReduceStock(itemId,amount);
        return mqResult;
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) {
        itemDOMapper.increaseSales(itemId,amount);
    }

    @Override
    public ItemModel getItemByIdInCache(Integer id) {
        ItemModel itemModel = (ItemModel) redisTemplate.opsForValue().get("item_validate_" + id);
        if (itemModel == null) {
            itemModel = this.getItemById(id);
            redisTemplate.opsForValue().set("item_validate_" + id,itemModel);
            redisTemplate.expire("item_validate_" + id,10, TimeUnit.MINUTES);
        }
        return itemModel;
    }

    @Override
    @Transactional
    //初始化对应的库存流水
    public String initStockLog(Integer itemId, Integer amount) {
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-",""));
        stockLogDO.setItemId(itemId);
        stockLogDO.setAmount(amount);
        stockLogDO.setStatus(1);
        stockLogDOMapper.insertSelective(stockLogDO);
        return stockLogDO.getStockLogId();
    }

    private ItemModel convertModelFromDataObject(ItemDO itemDO, ItemStockDO itemStockDO) {
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO,itemModel);
        itemModel.setPrice(BigDecimal.valueOf(itemDO.getPrice()));
        itemModel.setStock(itemStockDO.getStock());
        return itemModel;
    }

    private ItemDO convertItemDOFromModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel,itemDO);
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }

    private ItemStockDO convertItemStockDOFromModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());
        return itemStockDO;
    }
}
