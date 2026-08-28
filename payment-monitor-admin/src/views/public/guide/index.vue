<template>
  <div class="public-site guide-page">
    <PublicSiteHeader />

    <main>
      <section class="guide-hero">
        <div class="hero-grid"></div>
        <div class="site-container guide-hero-content">
          <div>
            <span class="eyebrow-pill">
              <span class="pulse-dot"></span>
              LULUPAY QUICK START
            </span>
            <h1>从安装监控端到完成<br />第一笔回调</h1>
            <p>
              按照账号、商户、设备、二维码、订单和回调的顺序完成配置，
              避免遗漏通知权限、动态金额或消费方幂等。
            </p>
            <div class="guide-hero-actions">
              <router-link class="button button-primary button-large" to="/register">
                免费注册
              </router-link>
              <router-link class="button button-light button-large" to="/#download">
                下载监控端
              </router-link>
            </div>
          </div>
          <div class="guide-checklist">
            <span>开始前请准备</span>
            <ul>
              <li><b>01</b> 可接收验证码的邮箱</li>
              <li><b>02</b> 一台可持续联网的 Android 手机</li>
              <li><b>03</b> 微信或支付宝收款二维码</li>
              <li><b>04</b> 用于测试回调的业务地址</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="guide-content-section">
        <div class="site-container guide-layout">
          <aside class="guide-sidebar">
            <div class="guide-sidebar-card">
              <strong>教程目录</strong>
              <button
                v-for="section in guideSections"
                :key="section.id"
                type="button"
                :class="{ active: activeSection === section.id }"
                @click="scrollToSection(section.id)"
              >
                <span>{{ section.eyebrow }}</span>
                {{ section.title }}
              </button>
            </div>
            <div class="guide-sidebar-note">
              <strong>重要说明</strong>
              <p>LuLuPay 当前依据到账通知确认状态，不等同于支付平台官方资金查询结果。</p>
            </div>
          </aside>

          <div class="guide-mobile-nav">
            <label for="guide-section">跳转到教程步骤</label>
            <select id="guide-section" :value="activeSection" @change="handleMobileSection">
              <option v-for="section in guideSections" :key="section.id" :value="section.id">
                {{ section.eyebrow }} · {{ section.title }}
              </option>
            </select>
          </div>

          <div class="guide-articles">
            <article
              v-for="section in guideSections"
              :id="section.id"
              :key="section.id"
              class="guide-article"
              :data-guide-section="section.id"
            >
              <div class="guide-article-heading">
                <span>{{ section.eyebrow }}</span>
                <h2>{{ section.title }}</h2>
                <p>{{ section.summary }}</p>
              </div>
              <ol>
                <li v-for="(step, index) in section.steps" :key="step">
                  <span>{{ String(index + 1).padStart(2, '0') }}</span>
                  <p>{{ step }}</p>
                </li>
              </ol>
              <div v-if="section.tips?.length" class="guide-tip">
                <strong>注意</strong>
                <p v-for="tip in section.tips" :key="tip">{{ tip }}</p>
              </div>
              <div v-if="section.id === 'install'" class="guide-inline-action">
                <div>
                  <strong>始终从官网获取最新 APK</strong>
                  <p>下载区域会展示版本、文件校验值、签名证书和更新说明。</p>
                </div>
                <router-link class="button button-primary" to="/#download">
                  前往下载
                </router-link>
              </div>
              <div v-if="section.id === 'test-order'" class="amount-example">
                <div>
                  <small>业务订单金额</small>
                  <strong>¥1.00</strong>
                </div>
                <span>动态金额分配 →</span>
                <div class="payable">
                  <small>本次实际应付</small>
                  <strong>¥1.01</strong>
                </div>
              </div>
            </article>

            <section class="guide-security-note">
              <span>SECURITY</span>
              <div>
                <h2>不要在教程截图和日志中暴露敏感信息</h2>
                <p>
                  业务密钥、Webhook Secret、MFA 密钥、恢复码、配对码、完整通知原文和下载 Token
                  均不应出现在公开截图、浏览器存储或普通日志中。
                </p>
              </div>
            </section>

            <section class="guide-finish">
              <div>
                <span class="section-eyebrow light-eyebrow">READY</span>
                <h2>完成配置后，先跑通小额测试闭环</h2>
                <p>确认设备在线、事件匹配、订单状态和回调 ACK 全部正常，再接入正式业务。</p>
              </div>
              <div>
                <router-link class="button button-light button-large" to="/register">
                  免费创建账号
                </router-link>
                <router-link class="button button-dark-outline button-large" to="/login">
                  登录控制台
                </router-link>
              </div>
            </section>
          </div>
        </div>
      </section>
    </main>

    <PublicSiteFooter />
  </div>
</template>

<script setup lang="ts">
import PublicSiteFooter from '../components/PublicSiteFooter.vue';
import PublicSiteHeader from '../components/PublicSiteHeader.vue';
import { guideSections } from '../site-content';
import { usePublicSeo } from '../use-public-seo';
import '../public-site.scss';

const route = useRoute();
const router = useRouter();
const activeSection = ref(guideSections[0].id);
let observer: IntersectionObserver | undefined;

usePublicSeo({
  title: 'LuLuPay 使用教程 - 安装、配对、二维码与回调接入',
  description:
    'LuLuPay 完整使用教程，包含账号注册、MFA、Android 安装、通知权限、设备配对、二维码、动态金额、易支付、API 与 Webhook。'
});

const scrollToSection = async (id: string) => {
  activeSection.value = id;
  await router.replace({ path: '/guide', hash: `#${id}` });
  document.querySelector(`#${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

const handleMobileSection = (event: Event) => {
  scrollToSection((event.target as HTMLSelectElement).value);
};

onMounted(() => {
  if (route.hash) {
    const target = route.hash.slice(1);
    if (guideSections.some(section => section.id === target)) {
      activeSection.value = target;
      requestAnimationFrame(() => {
        document.querySelector(route.hash)?.scrollIntoView({ block: 'start' });
      });
    }
  }

  if ('IntersectionObserver' in window) {
    observer = new IntersectionObserver(
      entries => {
        const visible = entries
          .filter(entry => entry.isIntersecting)
          .toSorted((left, right) => right.intersectionRatio - left.intersectionRatio)[0];
        const sectionId = visible?.target.getAttribute('data-guide-section');
        if (sectionId) {
          activeSection.value = sectionId;
        }
      },
      {
        rootMargin: '-120px 0px -55% 0px',
        threshold: [0.1, 0.35, 0.65]
      }
    );
    document.querySelectorAll('[data-guide-section]').forEach(element => observer?.observe(element));
  }
});

onBeforeUnmount(() => {
  observer?.disconnect();
});
</script>
