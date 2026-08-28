<template>
  <div class="public-site home-page">
    <PublicSiteHeader />

    <main>
      <section class="hero-section">
        <div class="hero-grid"></div>
        <div class="hero-orb hero-orb-one"></div>
        <div class="hero-orb hero-orb-two"></div>
        <div class="site-container hero-layout">
          <div class="hero-copy">
            <div class="eyebrow-pill">
              <span class="pulse-dot"></span>
              Android 通知监听 · 动态金额 · 可靠回调
            </div>
            <h1>
              把收款通知，
              <span>可靠地接入</span>
              你的业务系统
            </h1>
            <p class="hero-description">
              LuLuPay 码支付通过 Android 通知监听、动态金额匹配与可靠回调，
              连接微信、支付宝收款通知与业务订单。
            </p>
            <div class="hero-tags" aria-label="产品特点">
              <span>资金直达收款账户</span>
              <span>不接管商户资金</span>
              <span>支持易支付、API 与 Webhook</span>
            </div>
            <div class="hero-actions">
              <router-link class="button button-primary button-large" to="/register">
                免费开始使用
                <span aria-hidden="true">→</span>
              </router-link>
              <router-link class="button button-light button-large" to="/guide">
                查看使用教程
              </router-link>
              <button type="button" class="text-button" @click="scrollToDownload">
                下载监控端
                <span aria-hidden="true">↓</span>
              </button>
            </div>
          </div>

          <div class="hero-product" aria-label="LuLuPay 产品能力预览">
            <div class="console-preview">
              <div class="preview-topbar">
                <div class="preview-brand">
                  <img :src="logo" alt="" />
                  <span>LuLuPay 控制台</span>
                </div>
                <div class="preview-status"><span></span> 服务正常</div>
              </div>
              <div class="preview-body">
                <div class="preview-sidebar">
                  <span class="active"></span>
                  <span></span>
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
                <div class="preview-content">
                  <div class="preview-heading">
                    <div>
                      <small>今日收款概览</small>
                      <strong>订单与设备状态</strong>
                    </div>
                    <span class="preview-chip">实时更新</span>
                  </div>
                  <div class="preview-metrics">
                    <div>
                      <small>设备在线</small>
                      <strong>2 / 2</strong>
                      <span class="trend-positive">运行正常</span>
                    </div>
                    <div>
                      <small>待处理回调</small>
                      <strong>0</strong>
                      <span>自动重试</span>
                    </div>
                    <div>
                      <small>确认方式</small>
                      <strong>通知</strong>
                      <span>可升级确认</span>
                    </div>
                  </div>
                  <div class="preview-order-card">
                    <div class="order-card-title">
                      <span>最近订单</span>
                      <span>查看全部</span>
                    </div>
                    <div class="preview-order-row">
                      <span class="platform-mark wechat">微</span>
                      <div>
                        <strong>动态金额订单</strong>
                        <small>收银台应付 ¥1.01</small>
                      </div>
                      <span class="order-success">已匹配</span>
                    </div>
                    <div class="preview-order-row">
                      <span class="platform-mark alipay">支</span>
                      <div>
                        <strong>托管收银台订单</strong>
                        <small>Webhook 等待投递</small>
                      </div>
                      <span class="order-pending">回调中</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="workflow" class="section workflow-section">
        <div class="site-container">
          <div class="section-heading centered-heading">
            <span class="section-eyebrow">HOW IT WORKS</span>
            <h2>从业务订单到可靠回调，只需一条清晰链路</h2>
            <p>动态金额必须以托管收银台展示的实际应付金额为准。</p>
          </div>
          <div class="workflow-grid">
            <article v-for="step in workflowSteps" :key="step.index" class="workflow-card">
              <span class="workflow-index">{{ step.index }}</span>
              <div class="workflow-line"></div>
              <h3>{{ step.title }}</h3>
              <p>{{ step.description }}</p>
            </article>
          </div>
          <div class="amount-notice">
            <span class="notice-symbol">¥</span>
            <div>
              <strong>动态金额分配从最小可用偏移开始</strong>
              <p>
                已冻结槽位继续递增，订单完成或取消释放后，再从最小可用偏移重新分配，
                避免随机跳到较大的分值。
              </p>
            </div>
          </div>
        </div>
      </section>

      <section id="features" class="section features-section">
        <div class="site-container">
          <div class="section-heading centered-heading">
            <span class="section-eyebrow">CORE FEATURES</span>
            <h2>需要的核心功能，都集中在一条链路里</h2>
            <p>从手机监听、动态金额到订单匹配和可靠回调。</p>
          </div>
          <div class="feature-grid">
            <article v-for="feature in coreFeatures" :key="feature.index" class="feature-card">
              <span class="feature-index">{{ feature.index }}</span>
              <h3>{{ feature.title }}</h3>
              <p>{{ feature.description }}</p>
              <small>{{ feature.detail }}</small>
            </article>
          </div>
        </div>
      </section>

      <section class="section guide-preview-section">
        <div class="site-container guide-preview-shell">
          <div class="guide-preview-copy">
            <span class="section-eyebrow">QUICK START</span>
            <h2>从注册到第一笔测试订单</h2>
            <p>
              按照固定顺序配置账号、设备、二维码和回调，可以减少遗漏权限或误付动态金额的问题。
            </p>
            <router-link class="button button-primary button-large" to="/guide">
              打开完整教程
              <span aria-hidden="true">→</span>
            </router-link>
          </div>
          <ol class="quick-guide-list">
            <li v-for="(step, index) in quickGuideSteps" :key="step">
              <span>{{ String(index + 1).padStart(2, '0') }}</span>
              <strong>{{ step }}</strong>
            </li>
          </ol>
        </div>
      </section>

      <section id="pricing" class="section pricing-section">
        <div class="site-container">
          <div class="section-heading centered-heading">
            <span class="section-eyebrow">PLANS</span>
            <h2>从免费验证到团队级运营</h2>
            <p>首版不在官网公开价格，按设备、订单用量和服务能力选择版本。</p>
          </div>
          <div class="pricing-grid">
            <article
              v-for="plan in pricingPlans"
              :key="plan.name"
              class="pricing-card"
              :class="{ featured: plan.featured }"
            >
              <span v-if="plan.featured" class="recommended-label">推荐</span>
              <h3>{{ plan.name }}</h3>
              <p>{{ plan.caption }}</p>
              <div class="price-placeholder">
                <strong>{{ plan.name === '免费版' ? '永久免费' : '按需开通' }}</strong>
                <span>{{ plan.name === '免费版' ? '无需绑定支付方式' : '不按交易金额抽成' }}</span>
              </div>
              <ul>
                <li v-for="feature in plan.features" :key="feature">
                  <span>✓</span>
                  {{ feature }}
                </li>
              </ul>
              <router-link
                class="button button-block"
                :class="plan.featured ? 'button-primary' : 'button-outline'"
                :to="plan.actionPath"
              >
                {{ plan.action }}
              </router-link>
            </article>
          </div>
          <p class="pricing-note">
            商业化建议采用软件订阅、设备与订单用量阶梯以及企业增值服务，不按支付金额抽取手续费。
          </p>
        </div>
      </section>

      <section id="download" class="section download-section">
        <div class="site-container download-shell">
          <div class="download-copy">
            <span class="section-eyebrow light-eyebrow">ANDROID MONITOR</span>
            <h2>下载 LuLuPay<br />Android 监控端</h2>
            <p>
              只从官网获取最新版 APK。安装前可以核对文件 SHA-256 与签名证书 SHA-256，
              下载按钮每次都会刷新临时地址。
            </p>
            <div class="install-points">
              <span>① 下载并校验 APK</span>
              <span>② 授予通知访问权限</span>
              <span>③ 开启自启动与后台保护</span>
              <span>④ 输入配对码连接商户</span>
            </div>
          </div>

          <div class="release-card" data-testid="release-card">
            <template v-if="releaseState === 'loading'">
              <div class="release-loading">
                <span class="loading-ring"></span>
                <strong>正在获取最新版本</strong>
                <p>请稍候，系统正在读取公开发布信息。</p>
              </div>
            </template>

            <template v-else-if="releaseState === 'ready' && release">
              <div class="release-header">
                <div>
                  <span class="release-platform">ANDROID APK</span>
                  <h3>LuLuPay v{{ release.versionName }}</h3>
                </div>
                <span class="release-badge">最新正式版</span>
              </div>
              <div class="release-meta">
                <div>
                  <small>版本代码</small>
                  <strong>{{ release.versionCode }}</strong>
                </div>
                <div>
                  <small>文件大小</small>
                  <strong>{{ formatReleaseFileSize(release.fileSize) }}</strong>
                </div>
                <div>
                  <small>发布时间</small>
                  <strong>{{ formatReleaseDate(release.publishedAt) }}</strong>
                </div>
              </div>
              <div class="release-hashes">
                <div>
                  <span>
                    <small>APK SHA-256</small>
                    <code :title="release.sha256">{{ abbreviateHash(release.sha256) }}</code>
                  </span>
                  <button type="button" @click="copyValue(release.sha256, 'APK SHA-256')">复制</button>
                </div>
                <div>
                  <span>
                    <small>签名证书 SHA-256</small>
                    <code :title="release.signingCertificateSha256">
                      {{ abbreviateHash(release.signingCertificateSha256) }}
                    </code>
                  </span>
                  <button
                    type="button"
                    @click="copyValue(release.signingCertificateSha256, '签名证书 SHA-256')"
                  >
                    复制
                  </button>
                </div>
              </div>
              <div v-if="releaseNotes.length" class="release-notes">
                <strong>本次更新</strong>
                <ul>
                  <li v-for="note in releaseNotes" :key="note">{{ note }}</li>
                </ul>
              </div>
              <button
                type="button"
                class="button button-primary button-block button-download"
                :disabled="downloading"
                @click="downloadLatest"
              >
                {{ downloading ? '正在刷新下载地址…' : '下载 Android 监控端' }}
              </button>
              <router-link class="release-guide-link" to="/guide#install">
                查看安装与权限配置教程 →
              </router-link>
            </template>

            <template v-else-if="releaseState === 'empty'">
              <div class="release-empty">
                <span>APK</span>
                <strong>监控端暂未发布</strong>
                <p>当前没有可公开下载的 Android 版本，请稍后再试或先查看安装教程。</p>
                <router-link class="button button-outline button-block" to="/guide#install">
                  查看安装教程
                </router-link>
              </div>
            </template>

            <template v-else>
              <div class="release-empty release-error">
                <span>!</span>
                <strong>暂时无法获取版本信息</strong>
                <p>{{ releaseError || '请检查网络连接后重试，使用教程仍可正常查看。' }}</p>
                <button type="button" class="button button-outline button-block" @click="loadRelease">
                  重新获取
                </button>
                <router-link class="release-guide-link" to="/guide#install">
                  先查看安装教程 →
                </router-link>
              </div>
            </template>
          </div>
        </div>
      </section>

      <section class="section faq-section">
        <div class="site-container faq-layout">
          <div class="faq-heading">
            <span class="section-eyebrow">FAQ</span>
            <h2>接入前经常遇到的问题</h2>
            <p>更多安装、配对和回调排查步骤可在完整使用教程中查看。</p>
            <router-link class="text-link" to="/guide">打开使用教程 →</router-link>
          </div>
          <div class="faq-list">
            <details v-for="(item, index) in faqs" :key="item.question" :open="index === 0">
              <summary>
                <span>{{ item.question }}</span>
                <b aria-hidden="true">+</b>
              </summary>
              <p>{{ item.answer }}</p>
            </details>
          </div>
        </div>
      </section>

      <section class="final-cta-section">
        <div class="site-container final-cta">
          <div>
            <span class="section-eyebrow light-eyebrow">GET STARTED</span>
            <h2>准备好连接你的第一笔收款通知了吗？</h2>
            <p>从永久免费基础版开始，先完成一笔小额测试订单。</p>
          </div>
          <div class="final-cta-actions">
            <router-link class="button button-light button-large" to="/register">
              免费创建 LuLuPay 账号
            </router-link>
            <router-link class="button button-dark-outline button-large" to="/login">
              登录控制台
            </router-link>
          </div>
        </div>
      </section>
    </main>

    <PublicSiteFooter />
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { getLatestAndroidRelease, type PublicAndroidRelease } from '@/api/public/app-release';
import logo from '@/assets/logo/logo.png';
import { extractErrorMessage } from '@/utils/request';
import PublicSiteFooter from '../components/PublicSiteFooter.vue';
import PublicSiteHeader from '../components/PublicSiteHeader.vue';
import {
  coreFeatures,
  faqs,
  pricingPlans,
  quickGuideSteps,
  workflowSteps
} from '../site-content';
import {
  abbreviateHash,
  formatReleaseDate,
  formatReleaseFileSize,
  splitReleaseNotes
} from '../release-utils';
import { usePublicSeo } from '../use-public-seo';
import '../public-site.scss';

type ReleaseState = 'loading' | 'ready' | 'empty' | 'error';

const release = ref<PublicAndroidRelease>();
const releaseState = ref<ReleaseState>('loading');
const releaseError = ref('');
const downloading = ref(false);
const releaseNotes = computed(() => splitReleaseNotes(release.value?.releaseNotes));

usePublicSeo({
  title: 'LuLuPay - 码支付通知监控与业务回调',
  description:
    'LuLuPay 提供 Android 收款通知监听、动态金额、托管收银台、易支付兼容、Merchant API 与可靠 Webhook 回调。'
});

const loadRelease = async () => {
  releaseState.value = 'loading';
  releaseError.value = '';
  try {
    const response = await getLatestAndroidRelease();
    if (!response.data) {
      release.value = undefined;
      releaseState.value = 'empty';
      return;
    }
    release.value = response.data;
    releaseState.value = response.data.downloadUrl ? 'ready' : 'empty';
  } catch (error) {
    const message = (await extractErrorMessage(error)) || '';
    release.value = undefined;
    if (/未发布|不存在|暂无/.test(message)) {
      releaseState.value = 'empty';
      return;
    }
    releaseError.value = message;
    releaseState.value = 'error';
  }
};

const downloadLatest = async () => {
  if (downloading.value) {
    return;
  }
  downloading.value = true;
  try {
    const response = await getLatestAndroidRelease();
    const downloadUrl = response.data?.downloadUrl;
    if (!downloadUrl) {
      release.value = response.data;
      releaseState.value = 'empty';
      return;
    }
    release.value = response.data;
    const anchor = document.createElement('a');
    anchor.href = downloadUrl;
    anchor.rel = 'noopener';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  } catch (error) {
    releaseError.value = (await extractErrorMessage(error)) || '下载地址刷新失败，请稍后重试';
    releaseState.value = 'error';
  } finally {
    downloading.value = false;
  }
};

const copyValue = async (value: string, label: string) => {
  if (!value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success(`${label} 已复制`);
  } catch {
    const textarea = document.createElement('textarea');
    textarea.value = value;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    textarea.remove();
    ElMessage.success(`${label} 已复制`);
  }
};

const scrollToDownload = () => {
  document.querySelector('#download')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

onMounted(loadRelease);
</script>
