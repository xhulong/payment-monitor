export interface WebsiteFeature {
  index: string;
  title: string;
  description: string;
  detail: string;
}

export interface WebsitePlan {
  name: string;
  caption: string;
  featured?: boolean;
  action: string;
  actionPath: string;
  features: string[];
}

export interface WebsiteFaq {
  question: string;
  answer: string;
}

export interface GuideSection {
  id: string;
  eyebrow: string;
  title: string;
  summary: string;
  steps: string[];
  tips?: string[];
}

export const workflowSteps = [
  {
    index: '01',
    title: '创建业务订单',
    description: '业务系统通过托管收银台、易支付或 Merchant API 创建订单。'
  },
  {
    index: '02',
    title: '展示应付金额',
    description: '系统分配二维码和动态金额，托管收银台明确展示本次实际应付金额。'
  },
  {
    index: '03',
    title: 'Android 捕获通知',
    description: '监控端在本地解析微信、支付宝到账通知并上传结构化事件。'
  },
  {
    index: '04',
    title: '匹配并回调',
    description: '服务端匹配订单，并通过 Webhook 或易支付通知业务系统。'
  }
] as const;

export const coreFeatures: WebsiteFeature[] = [
  {
    index: '01',
    title: '到账通知解析',
    description: '覆盖微信与支付宝收款方向识别、金额提取和负样本过滤。',
    detail: '未识别通知保留本地诊断信息，不直接进入支付队列。'
  },
  {
    index: '02',
    title: 'Android 设备协同',
    description: '支持配对、心跳、在线状态、健康检查和应用内版本更新。',
    detail: '管理端能够及时查看通知权限、监听连接和后台运行状态。'
  },
  {
    index: '03',
    title: '动态金额匹配',
    description: '优先使用最小可用偏移，冻结槽位释放后可以再次复用。',
    detail: '托管收银台向付款人展示准确应付金额，避免金额理解偏差。'
  },
  {
    index: '04',
    title: '多种接入方式',
    description: '兼容易支付经典协议，同时提供 Merchant API 与 Webhook。',
    detail: '现有业务可以按自身技术栈选择托管收银台或接口接入。'
  },
  {
    index: '05',
    title: '可靠异步回调',
    description: '通过 Outbox、幂等、自动重试、DEAD 和人工重放管理回调。',
    detail: '每次投递保留脱敏响应、耗时和失败原因，方便问题定位。'
  },
  {
    index: '06',
    title: '确认与安全',
    description: '区分通知、人工和对账确认，并提供 MFA 与敏感操作审计。',
    detail: '高价值业务可以提高确认等级，不只依赖到账通知完成交付。'
  }
];

export const quickGuideSteps = [
  '注册账号并完成邮箱验证',
  '下载监控端并完成设备配对',
  '添加微信或支付宝收款二维码',
  '创建订单并按实际金额付款',
  '配置易支付、API 或 Webhook'
] as const;

export const pricingPlans: WebsitePlan[] = [
  {
    name: '免费版',
    caption: '适合个人项目和接入验证',
    action: '免费开始使用',
    actionPath: '/register',
    features: ['1 台 Android 设备', '每月 1,000 笔确认订单', '1 个接入应用']
  },
  {
    name: '专业版',
    caption: '适合稳定运行的小型业务',
    featured: true,
    action: '申请开通',
    actionPath: '/register?plan=professional',
    features: ['3 台 Android 设备', '每月 20,000 笔确认订单', '回调重试与告警']
  },
  {
    name: '团队版',
    caption: '适合多人协作和增长业务',
    action: '申请开通',
    actionPath: '/register?plan=team',
    features: ['10 台 Android 设备', '每月 100,000 笔确认订单', '成员角色与审计']
  },
  {
    name: '企业版',
    caption: '适合定制、私有化与高用量',
    action: '联系平台',
    actionPath: '/register?plan=enterprise',
    features: ['自定义设备和订单额度', '私有部署与迁移', '专属支持与 SLA']
  }
];

export const faqs: WebsiteFaq[] = [
  {
    question: '为什么订单金额和实际付款金额可能不同？',
    answer:
      '为了在相同时间内区分多笔同额订单，系统可能分配一个较小的动态金额偏移。请始终以 LuLuPay 托管收银台展示的实际应付金额为准。'
  },
  {
    question: 'Android 设备离线应该怎么处理？',
    answer:
      '先检查通知访问权限、监听服务、网络连接、自启动和电池优化设置，再在管理端确认设备心跳与健康状态。完整步骤可在使用教程中查看。'
  },
  {
    question: '已经收到付款通知，但订单没有匹配怎么办？',
    answer:
      '可以在支付事件中检查平台、方向、金额和发生时间。未识别或存在冲突的事件会保留诊断信息，必要时可进行人工确认。'
  },
  {
    question: 'Webhook 没有成功回调怎么办？',
    answer:
      '在回调记录中检查 HTTP 状态、脱敏响应和失败原因。系统会自动重试，进入 DEAD 后可以排除问题并执行人工重放。'
  }
];

export const guideSections: GuideSection[] = [
  {
    id: 'account',
    eyebrow: 'STEP 01',
    title: '注册账号并配置多因素认证',
    summary: '先完成邮箱验证和账号安全设置，再进入商户入驻流程。',
    steps: [
      '打开注册页面，填写登录用户名、昵称和常用邮箱。',
      '完成图片验证码与邮件验证码验证，设置 12–64 位登录密码。',
      '登录后根据引导配置多因素认证（MFA），妥善保存恢复码。',
      '在个人中心确认邮箱和账号安全状态均正常。'
    ],
    tips: ['恢复码每枚只能使用一次，不要与密码、认证器密钥存放在同一位置。']
  },
  {
    id: 'onboarding',
    eyebrow: 'STEP 02',
    title: '提交商户入驻资料',
    summary: '填写真实的经营场景和计划使用的收款平台，等待平台审核。',
    steps: [
      '进入商户入驻页面，填写基本资料、经营信息和计划收款平台。',
      '先保存草稿并检查必填项，再提交审核。',
      '如收到补充资料通知，根据审核意见修改后重新提交。',
      '审核通过后按照开通清单配置二维码、设备和测试通知。'
    ]
  },
  {
    id: 'install',
    eyebrow: 'STEP 03',
    title: '下载并校验 Android 监控端',
    summary: '只从 LuLuPay 官网下载最新版 APK，并在安装前核对版本与校验值。',
    steps: [
      '在官网“下载监控端”区域查看最新版本、APK SHA-256 和签名证书 SHA-256。',
      '点击下载前系统会重新获取临时下载地址，避免使用已经过期的链接。',
      '下载完成后按需核对 APK SHA-256，再允许系统安装未知来源应用。',
      '安装时确认应用名称为 LuLuPay，包名与官网说明一致。'
    ],
    tips: ['不要安装聊天群、网盘或未知网站提供的所谓修改版 APK。']
  },
  {
    id: 'permissions',
    eyebrow: 'STEP 04',
    title: '授予通知与后台运行权限',
    summary: '通知访问权限和持续后台运行是捕获到账通知的前提。',
    steps: [
      '在系统设置中允许 LuLuPay 使用通知访问权限。',
      '允许前台服务通知，避免系统静默停止监听服务。',
      '开启自启动或后台启动权限，并将电池策略调整为不限制。',
      '返回 LuLuPay 检查通知使用权、监听连接、前台服务和通知权限状态。'
    ],
    tips: ['不同手机品牌入口名称可能不同，红米/小米设备还需要检查省电策略和后台保护。']
  },
  {
    id: 'pairing',
    eyebrow: 'STEP 05',
    title: '生成配对码并连接设备',
    summary: '配对码用于把 Android 监控端绑定到当前商户。',
    steps: [
      '在管理端进入“支付运营 → 设备管理”，点击新增或生成配对码。',
      '在手机 LuLuPay 中输入配对码，确认连接到正确的服务端地址。',
      '配对成功后电脑端会自动刷新设备列表并显示设备名称。',
      '确认设备在线、心跳正常且应用版本符合最低版本要求。'
    ],
    tips: ['配对码具有有效期，不要通过日志、URL 或公开截图传播。']
  },
  {
    id: 'qrcode',
    eyebrow: 'STEP 06',
    title: '添加收款二维码',
    summary: '微信和支付宝二维码需要分别配置，并与设备及支付路由保持同一商户。',
    steps: [
      '进入“支付运营 → 收款二维码”，选择微信或支付宝平台。',
      '填写二维码名称、内容模板和状态，保存后确认二维码可用。',
      '如果使用易支付接入，在支付接入页面把支付类型路由到对应二维码。',
      '使用小额测试订单检查二维码内容和托管收银台展示。'
    ]
  },
  {
    id: 'test-order',
    eyebrow: 'STEP 07',
    title: '创建并完成第一笔测试订单',
    summary: '测试时必须支付托管收银台展示的实际金额，而不是只看原始订单金额。',
    steps: [
      '创建一笔小额测试订单并打开托管收银台。',
      '确认页面同时区分订单金额和实际应付金额。',
      '使用对应平台扫描二维码，严格按实际应付金额付款。',
      '检查 Android 是否捕获通知、订单是否匹配以及确认状态是否更新。'
    ],
    tips: ['如果实际应付金额为 1.01 元，就需要支付 1.01 元，支付 1.00 元不会命中该金额槽位。']
  },
  {
    id: 'integration',
    eyebrow: 'STEP 08',
    title: '选择业务接入方式',
    summary: '根据现有系统改造成本选择易支付、Merchant API 或 Webhook。',
    steps: [
      '易支付系统优先使用托管收银台模式，由 LuLuPay 展示准确应付金额。',
      '自研系统可以通过 Merchant API 创建订单和查询状态。',
      '需要异步推进业务时配置 Webhook，并在消费方验证签名和 eventId 幂等。',
      '不要在浏览器、本地存储或日志中保存业务密钥。'
    ]
  },
  {
    id: 'callback',
    eyebrow: 'STEP 09',
    title: '测试回调与失败重试',
    summary: '在正式接入前验证成功回调、重复回调和失败恢复流程。',
    steps: [
      '使用测试订单触发一次完整的订单确认和回调。',
      '在回调记录中核对目标地址、HTTP 状态、ACK、耗时和脱敏响应。',
      '模拟超时或非成功响应，确认系统进入自动重试。',
      '排除消费方问题后，对 DEAD 任务执行人工重放并记录原因。'
    ]
  },
  {
    id: 'troubleshooting',
    eyebrow: 'CHECKLIST',
    title: '常见问题排查顺序',
    summary: '按链路从手机到业务系统逐层排查，比反复重新付款更有效。',
    steps: [
      '设备离线：检查网络、心跳、监听服务、自启动和电池策略。',
      '没有通知：确认微信/支付宝确实产生到账通知，并检查通知访问权限。',
      '事件未匹配：检查商户、平台、方向、实际金额和订单有效时间。',
      '回调失败：检查域名白名单、HTTPS、签名验证、ACK 和消费方幂等。',
      '版本异常：从官网重新获取最新 APK，核对 SHA-256 和签名证书。'
    ]
  }
];
