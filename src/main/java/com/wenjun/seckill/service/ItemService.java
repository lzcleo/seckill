package com.wenjun.seckill.service;

import com.wenjun.seckill.error.BusinessException;
import com.wenjun.seckill.service.model.ItemModel;

import java.util.List;

/**
 * @Author: wenjun
 * @Date: 2019/12/20 15:02
 */
public interface ItemService {
    //创建商品
    ItemModel createItem(ItemModel itemModel) throws BusinessException;
    //商品列表
    List<ItemModel> listItem();
    //通过ID获得商品信息
    ItemModel getItemById(Integer id);
    //下单后减库存
    boolean decreaseStock(Integer itemId,Integer amount);
    //下单后在Redis中减库存
    boolean decreaseStockInRedis(Integer itemId,Integer amount);
    //库存回滚
    boolean increaseStockInRedis(Integer itemId,Integer amount);
    //通过RocketMQ异步减库存
    boolean asyncDecreaseStock(Integer itemId,Integer amount);
    //下单后增销量
    void increaseSales(Integer itemId,Integer amount);
    //item及promo model缓存模型
    ItemModel getItemByIdInCache(Integer id);
    //初始化库存流水
    String initStockLog(Integer itemId, Integer amount);
}
