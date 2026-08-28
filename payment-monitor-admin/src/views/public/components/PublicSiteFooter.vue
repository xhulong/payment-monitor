<template>
  <footer class="site-footer">
    <div class="site-container footer-main">
      <div class="footer-brand">
        <div class="site-brand footer-logo">
          <img :src="logo" alt="LuLuPay Logo" />
          <span>
            <strong>LuLuPay</strong>
            <small>码支付</small>
          </span>
        </div>
        <p>连接收款通知、业务订单与可靠回调，让每一次确认都有迹可循。</p>
      </div>
      <div class="footer-links">
        <div>
          <strong>开始使用</strong>
          <router-link to="/register">免费注册</router-link>
          <router-link to="/login">登录控制台</router-link>
          <router-link to="/guide">使用教程</router-link>
        </div>
        <div>
          <strong>产品能力</strong>
          <router-link to="/#workflow">工作原理</router-link>
          <router-link to="/#features">核心能力</router-link>
          <router-link to="/#download">监控端下载</router-link>
        </div>
      </div>
    </div>
    <div class="site-container footer-bottom">
      <span>© {{ currentYear }} LuLuPay · 码支付</span>
      <span>当前依据到账通知确认支付状态，不等同于微信或支付宝官方资金确认。</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
import logo from '@/assets/logo/logo.png';

const currentYear = new Date().getFullYear();
</script>
