package com.hmall.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.utils.BeanUtils;
import com.hmall.domain.dto.ItemDTO;
import com.hmall.domain.po.Item;
import com.hmall.domain.query.ItemPageQuery;
import com.hmall.service.IItemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "搜索相关接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final IItemService itemService;

    @ApiOperation("搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        // 1. 构建分页对象
        Page<Item> page = new Page<>(query.getPageNo(), query.getPageSize());

        // 2. 链式构建查询条件
        LambdaQueryChainWrapper<Item> wrapper = itemService.lambdaQuery()
                // 业务硬性指标：必须是上架商品 (Fix 漏洞)
                .eq(Item::getStatus, 1) //
                // 模糊搜索
                .like(StrUtil.isNotBlank(query.getKey()), Item::getName, query.getKey())
                // 过滤条件
                .eq(StrUtil.isNotBlank(query.getCategory()), Item::getCategory, query.getCategory())
                .eq(StrUtil.isNotBlank(query.getBrand()), Item::getBrand, query.getBrand())
                // 价格区间
                .ge(query.getMinPrice() != null, Item::getPrice, query.getMinPrice())
                .le(query.getMaxPrice() != null, Item::getPrice, query.getMaxPrice());

        // 3. 处理排序
        String sortBy = query.getSortBy();
        if ("price".equals(sortBy)) {
            wrapper.orderBy(true, query.getIsAsc(), Item::getPrice);
        } else if ("sold".equals(sortBy)) {
            wrapper.orderBy(true, query.getIsAsc(), Item::getSold);
        } else {
            // 默认按更新时间降序
            wrapper.orderByDesc(Item::getUpdateTime);
        }

        // 4. 执行查询
        Page<Item> result = wrapper.page(page);

        // 5. 返回
        return PageDTO.of(result, ItemDTO.class);

    }
}
