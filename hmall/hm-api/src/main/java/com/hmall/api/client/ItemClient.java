package com.hmall.api.client;



import com.hmall.api.dto.ItemDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

//标注是一个feign客户端然后指定了微服务名称，这样子可以获取到该微服务的服务列表
// 并基于负载均衡选择一个服务实例
@FeignClient("item-service")
public interface ItemClient {

    //在接口内 编写要远程调用的方法：这些方法都可以参考自服务提供者对应的接口

    //根据商品id集合获取商品dto列表
    @GetMapping("/items")
    public List<ItemDTO> queryItemByIds(@RequestParam("ids") Collection<Long> ids);
}
