package com.savor.dto;

import cn.hutool.core.bean.BeanUtil;
import com.savor.entity.Blog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BlogForm 入参对象测试：验证"字段注入防护"与 PO 转换的正确性（纯内存，不依赖容器）。
 */
class BlogFormTest {

    @Test
    @DisplayName("正常填写：四个字段完整拷贝到 Blog")
    void testCopyToBlog() {
        BlogForm form = new BlogForm();
        form.setTitle("百年老店的肠粉");
        form.setShopId(1L);
        form.setImages("a.jpg,b.jpg");
        form.setContent("皮薄馅靓，值得一试");

        Blog blog = BeanUtil.copyProperties(form, Blog.class);

        assertEquals("百年老店的肠粉", blog.getTitle());
        assertEquals(1L, blog.getShopId());
        assertEquals("a.jpg,b.jpg", blog.getImages());
        assertEquals("皮薄馅靓，值得一试", blog.getContent());
    }

    @Test
    @DisplayName("注入防护：前端伪造 liked/comments 字段在绑定阶段就被丢弃")
    void testInjectionFieldsIgnored() {
        // 模拟前端提交的完整 JSON 里混入恶意字段
        Map<String, Object> malicious = new java.util.HashMap<>();
        malicious.put("title", "测试标题");
        malicious.put("shopId", 1L);
        malicious.put("content", "内容");
        malicious.put("liked", 99999);
        malicious.put("comments", 88888);

        // BlogForm 只有4个字段：liked/comments 在反序列化阶段即被丢弃
        BlogForm form = BeanUtil.mapToBean(malicious, BlogForm.class, true);
        Blog blog = BeanUtil.copyProperties(form, Blog.class);

        assertEquals("测试标题", blog.getTitle());
        assertNull(blog.getLiked(), "liked 应未被赋值（入库时由 DB 默认值 0 兜底）");
        assertNull(blog.getComments(), "comments 应未被赋值");
    }

    @Test
    @DisplayName("空表单：转换不抛异常，字段全空")
    void testEmptyForm() {
        Blog blog = BeanUtil.copyProperties(new BlogForm(), Blog.class);
        assertNotNull(blog);
        assertNull(blog.getTitle());
        assertNull(blog.getShopId());
    }
}
