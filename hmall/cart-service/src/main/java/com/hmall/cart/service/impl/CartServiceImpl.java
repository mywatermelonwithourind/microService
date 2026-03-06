package com.hmall.cart.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.CollUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.cart.domain.dto.CartFormDTO;
import com.hmall.cart.domain.dto.ItemDTO;
import com.hmall.cart.domain.po.Cart;
import com.hmall.cart.domain.vo.CartVO;
import com.hmall.cart.mapper.CartMapper;
import com.hmall.cart.service.ICartService;
//import com.hmall.cart.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {

//    private final IItemService itemService;
    private final CartMapper cartMapper;
    private static final int MAX_CART_ITEMS = 10;
    private  final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;

    @Override
    public void addItem2Cart(CartFormDTO cartFormDTO) {
        // 0. 健壮性校验 (防止空指针)
        if(cartFormDTO ==null || cartFormDTO.getItemId() == null){
            throw new BadRequestException("参数不能为空");
        }

        // 1. 提取上下文，避免重复调用
        Long userId = UserContext.getUser();
        Long itemId = cartFormDTO.getItemId();

        // 2. 优先尝试更新数量

        boolean updateResult = lambdaUpdate()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getItemId, itemId)
                .setSql("num = num + 1")
                .update();

        if(!updateResult){
            // 3. 如果更新数量失败，说明购物车中没有该商品，就进行判断
            Long count = cartMapper.selectCount(Wrappers.<Cart>lambdaQuery()
                    .eq(Cart::getUserId, userId));

            if(count>=MAX_CART_ITEMS){
                throw new BizIllegalException("购物车中商品数量已达上限");
            }

            // 4. 构建Cart对象，插入到数据库
            Cart cart = BeanUtils.toBean(cartFormDTO, Cart.class);
            cart.setUserId(userId).setNum(1);
            cartMapper.insert(cart);
        }
    }

    @Override
    public List<CartVO> queryMyCarts() {
        //1.获取当前登录的用户id
//        Long userId = UserContext.getUser();
       //2.查询该用户的购物车列表
        List<Cart> cartList = lambdaQuery()
                .eq(Cart::getUserId, 1L)
                .list();
        //3.如果购物车为空，直接返回空列表
        if (CollUtils.isEmpty(cartList)) {
            return CollUtils.emptyList();
        }

        // 4. 提取所有商品ID
        Set<Long> itemIds = cartList.stream().map(Cart::getItemId).collect(Collectors.toSet());
        // 5. 批量查询商品信息
//        List<ItemDTO> itemDTOList = itemService.queryItemByIds(itemIds);
        // 获取注册中心item-service的服务列表
        List<ServiceInstance> instanceList = discoveryClient.getInstances("item-service");

        //从上面的服务列表中随机选择一个服务实例
        ServiceInstance serviceInstance = instanceList.get(RandomUtil.randomInt(instanceList.size()));

        //可以从实例中获得商品微服务的访问


//        String url="http://localhost:8081/items?ids={ids}";
        String url=serviceInstance.getUri()+"/items?ids={ids}";
        ResponseEntity<List<ItemDTO>> response = restTemplate.exchange(
                url,//请求路径
                HttpMethod.GET,//请求方式
                null,//请求体
                new ParameterizedTypeReference<List<ItemDTO>>() {
                },//响应的数据类型
                Map.of("ids", CollUtils.join(itemIds, ","))//请求参数
        );
        List<ItemDTO> itemDTOList =null;
        if (response.getStatusCode().is2xxSuccessful()) {
            itemDTOList = response.getBody();
            System.out.println("远程调用商品服务成功，返回数据：" + itemDTOList);
        }
        // 5.1 防御：如果商品服务查不到任何数据
        if (CollUtils.isEmpty(itemDTOList)) {
            return BeanUtils.copyList(cartList, CartVO.class);
        }
        // 6. 将商品列表转为 Map，方便后续 O(1) 复杂度获取
        Map<Long, ItemDTO> itemDTOMap = itemDTOList.stream()
                .collect(Collectors.toMap(ItemDTO::getId, Function.identity()));
        //7.组装数据
        List<CartVO> cartVOS = BeanUtils.copyList(cartList, CartVO.class);
        for (CartVO cartVO : cartVOS) {
            ItemDTO itemDTO = itemDTOMap.get(cartVO.getItemId());
            if (itemDTO != null) {
                cartVO.setNewPrice(itemDTO.getPrice()); //以此处的最新价格为准
                cartVO.setStatus(itemDTO.getStatus());
                cartVO.setStock(itemDTO.getStock());
            }
        }

        return cartVOS;
    }

    @Override
    public void removeByItemIds(Collection<Long> itemIds) {
        // 1.构建删除条件，userId和itemId
        QueryWrapper<Cart> queryWrapper = new QueryWrapper<Cart>();
        queryWrapper.lambda()
                .eq(Cart::getUserId, UserContext.getUser())
                .in(Cart::getItemId, itemIds);
        // 2.删除
        remove(queryWrapper);
    }
}
