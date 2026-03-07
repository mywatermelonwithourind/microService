package com.hmall.api.client;

import com.hmall.api.config.DefaultFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(value="cart-service",configuration = DefaultFeignConfig.class)
public interface CartClient {

    //批量删除购物车中商品
    @DeleteMapping("/carts")
    public void deleteCartItemByIds(@RequestParam("ids") List<Long> ids);
}
