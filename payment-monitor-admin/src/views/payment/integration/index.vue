<template>
  <div class="p-2 payment-integration-page">
    <el-alert
      title="易支付当前基于 Android 到账通知进行确认，不代表微信或支付宝官方资金确认。高价值业务建议选择人工确认或对账确认。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-card shadow="hover" class="mt-3">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="接入应用" name="applications">
          <el-form ref="applicationQueryRef" :model="applicationQuery" :inline="true">
            <el-form-item label="应用名称" prop="integrationName">
              <el-input
                v-model="applicationQuery.integrationName"
                clearable
                placeholder="应用名称"
                @keyup.enter="searchApplications"
              />
            </el-form-item>
            <el-form-item label="PID" prop="pid">
              <el-input
                v-model="applicationQuery.pid"
                clearable
                placeholder="数字 PID"
                @keyup.enter="searchApplications"
              />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select
                v-model="applicationQuery.status"
                clearable
                placeholder="全部状态"
                style="width: 130px"
              >
                <el-option label="启用" value="0" />
                <el-option label="停用" value="1" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="searchApplications">搜索</el-button>
              <el-button icon="Refresh" @click="resetApplicationQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="table-toolbar">
            <div>
              <h3>易支付接入应用</h3>
              <p>每个应用拥有独立 PID、业务密钥、回调策略和支付路由。</p>
            </div>
            <el-button
              v-hasPermi="['payment:integration:add']"
              type="primary"
              icon="Plus"
              @click="openCreateApplication"
            >
              新建接入应用
            </el-button>
          </div>

          <el-table v-loading="applicationLoading" :data="applications" border>
            <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
              <template #default="{ row }">
                <strong>{{ row.merchantName || '-' }}</strong>
                <div class="muted">{{ row.merchantCode || row.merchantId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="应用" min-width="220">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  @click="openApplicationDetail(row as PaymentIntegrationVO)"
                >
                  {{ row.integrationName }}
                </el-button>
                <div class="muted">{{ row.integrationCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="PID" prop="pid" min-width="145" />
            <el-table-column label="协议" width="150" align="center">
              <template #default="{ row }">
                <el-tag type="info">{{ row.profile }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="确认策略" min-width="150">
              <template #default="{ row }">{{ callbackPolicyLabel(row.callbackPolicy) }}</template>
            </el-table-column>
            <el-table-column label="通知" width="90" align="center">
              <template #default="{ row }">{{ row.notifyMethod }}</template>
            </el-table-column>
            <el-table-column label="当前密钥" width="105" align="center">
              <template #default="{ row }">v{{ row.activeSecretVersion || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === '0' ? 'success' : 'info'">
                  {{ row.status === '0' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="190">
              <template #default="{ row }">{{ formatApiTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="310" align="center">
              <template #default="{ row }">
                <el-button
                  v-hasPermi="['payment:integration:edit']"
                  link
                  type="primary"
                  @click="openEditApplication(row as PaymentIntegrationVO)"
                >
                  编辑
                </el-button>
                <el-button
                  v-hasPermi="['payment:integration:route']"
                  link
                  type="primary"
                  @click="openRoutes(row as PaymentIntegrationVO)"
                >
                  路由
                </el-button>
                <el-button
                  v-hasPermi="['payment:integration:secret']"
                  link
                  type="warning"
                  @click="rotateSecret(row as PaymentIntegrationVO)"
                >
                  轮换密钥
                </el-button>
                <el-button
                  v-hasPermi="['payment:integration:edit']"
                  link
                  :type="row.status === '0' ? 'danger' : 'success'"
                  @click="toggleApplication(row as PaymentIntegrationVO)"
                >
                  {{ row.status === '0' ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="applicationTotal > 0"
            v-model:page="applicationQuery.pageNum"
            v-model:limit="applicationQuery.pageSize"
            :total="applicationTotal"
            @pagination="loadApplications"
          />
        </el-tab-pane>

        <el-tab-pane label="支付路由" name="routes">
          <div class="route-heading">
            <div>
              <h3>支付路由</h3>
              <p>将易支付的微信或支付宝请求分配到当前商户已启用的收款二维码。</p>
            </div>
            <el-select
              v-model="selectedIntegrationId"
              filterable
              placeholder="选择接入应用"
              style="width: 300px"
              @change="loadRoutes"
            >
              <el-option
                v-for="item in integrationOptions"
                :key="item.id"
                :label="`${item.integrationName}（PID ${item.pid}）`"
                :value="item.id"
              />
            </el-select>
          </div>

          <el-empty v-if="!selectedIntegrationId" description="请先选择接入应用" />
          <template v-else>
            <el-table v-loading="routeLoading" :data="routeForms" border>
              <el-table-column label="支付类型" width="130" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.platform === 'WECHAT' ? 'success' : 'primary'">
                    {{ row.platform === 'WECHAT' ? '微信 / wxpay' : '支付宝 / alipay' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="收款二维码" min-width="260">
                <template #default="{ row }">
                  <el-select
                    v-model="row.qrAssetId"
                    filterable
                    placeholder="选择已启用二维码"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="asset in qrAssetsForPlatform(row.platform)"
                      :key="asset.id"
                      :label="`${asset.assetName}（${asset.assetCode}）`"
                      :value="asset.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="优先级" width="160">
                <template #default="{ row }">
                  <el-input-number v-model="row.priority" :min="1" :max="9999" />
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120" align="center">
                <template #default="{ row }">
                  <el-switch
                    :model-value="row.status === '0'"
                    active-text="启用"
                    inactive-text="停用"
                    @change="value => (row.status = value ? '0' : '1')"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="removeRoute($index)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="route-actions">
              <div>
                <el-button
                  v-hasPermi="['payment:integration:route']"
                  icon="Plus"
                  @click="addRoute('WECHAT')"
                >
                  新增微信路由
                </el-button>
                <el-button
                  v-hasPermi="['payment:integration:route']"
                  icon="Plus"
                  @click="addRoute('ALIPAY')"
                >
                  新增支付宝路由
                </el-button>
              </div>
              <el-button
                v-hasPermi="['payment:integration:route']"
                type="primary"
                :loading="routeSaving"
                @click="saveRoutes"
              >
                保存支付路由
              </el-button>
            </div>
          </template>
        </el-tab-pane>

        <el-tab-pane label="外部订单" name="orders">
          <el-form ref="orderQueryRef" :model="orderQuery" :inline="true">
            <el-form-item label="接入应用" prop="integrationId">
              <el-select
                v-model="orderQuery.integrationId"
                clearable
                filterable
                placeholder="全部应用"
                style="width: 230px"
              >
                <el-option
                  v-for="item in integrationOptions"
                  :key="item.id"
                  :label="item.integrationName"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="外部订单号" prop="externalOrderNo">
              <el-input v-model="orderQuery.externalOrderNo" clearable />
            </el-form-item>
            <el-form-item label="网关订单号" prop="gatewayTradeNo">
              <el-input v-model="orderQuery.gatewayTradeNo" clearable />
            </el-form-item>
            <el-form-item label="风险状态" prop="riskStatus">
              <el-select
                v-model="orderQuery.riskStatus"
                clearable
                placeholder="全部"
                style="width: 160px"
              >
                <el-option label="正常" value="NORMAL" />
                <el-option label="确认已撤销" value="CONFIRMATION_REVOKED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="searchOrders">搜索</el-button>
              <el-button icon="Refresh" @click="resetOrderQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="orderLoading" :data="orders" border>
            <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
              <template #default="{ row }">
                <strong>{{ row.merchantName || '-' }}</strong>
                <div class="muted">{{ row.merchantCode || row.merchantId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="外部订单号" min-width="190">
              <template #default="{ row }">
                <el-button link type="primary" @click="openOrderDetail(row.id)">
                  {{ row.externalOrderNo }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="网关订单号" prop="gatewayTradeNo" min-width="210" />
            <el-table-column label="接入应用" prop="integrationName" min-width="160" />
            <el-table-column label="平台" width="100" align="center">
              <template #default="{ row }">
                {{ row.platform === 'WECHAT' ? '微信' : row.platform === 'ALIPAY' ? '支付宝' : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="请求金额" width="115" align="right">
              <template #default="{ row }">{{ formatAmount(row.requestAmountMinor) }}</template>
            </el-table-column>
            <el-table-column label="实付金额" width="115" align="right">
              <template #default="{ row }">{{ formatAmount(row.payableAmountMinor) }}</template>
            </el-table-column>
            <el-table-column label="确认等级" width="120">
              <template #default="{ row }">{{ confirmationStatusLabel(row.confirmationStatus) }}</template>
            </el-table-column>
            <el-table-column label="回调状态" width="120">
              <template #default="{ row }">{{ callbackStatusLabel(row.callbackStatus) }}</template>
            </el-table-column>
            <el-table-column label="风险" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.riskStatus === 'NORMAL' ? 'success' : 'danger'">
                  {{ row.riskStatus === 'NORMAL' ? '正常' : '需人工处理' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="190">
              <template #default="{ row }">{{ formatApiTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="orderTotal > 0"
            v-model:page="orderQuery.pageNum"
            v-model:limit="orderQuery.pageSize"
            :total="orderTotal"
            @pagination="loadOrders"
          />
        </el-tab-pane>

        <el-tab-pane label="回调记录" name="callbacks">
          <el-form ref="callbackQueryRef" :model="callbackQuery" :inline="true">
            <el-form-item label="接入应用" prop="integrationId">
              <el-select
                v-model="callbackQuery.integrationId"
                clearable
                filterable
                placeholder="全部应用"
                style="width: 230px"
              >
                <el-option
                  v-for="item in integrationOptions"
                  :key="item.id"
                  :label="item.integrationName"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="Delivery ID" prop="deliveryId">
              <el-input v-model="callbackQuery.deliveryId" clearable />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select
                v-model="callbackQuery.status"
                clearable
                placeholder="全部状态"
                style="width: 150px"
              >
                <el-option label="等待投递" value="PENDING" />
                <el-option label="投递中" value="DELIVERING" />
                <el-option label="等待重试" value="RETRYING" />
                <el-option label="已送达" value="DELIVERED" />
                <el-option label="已停止重试" value="DEAD" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="searchCallbacks">搜索</el-button>
              <el-button icon="Refresh" @click="resetCallbackQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="callbackLoading" :data="callbacks" border>
            <el-table-column v-if="showMerchantColumn" label="商户" min-width="190">
              <template #default="{ row }">
                <strong>{{ row.merchantName || '-' }}</strong>
                <div class="muted">{{ row.merchantCode || row.merchantId }}</div>
              </template>
            </el-table-column>
            <el-table-column label="Delivery ID" min-width="220">
              <template #default="{ row }">
                <el-button link type="primary" @click="openCallbackDetail(row.id)">
                  {{ row.deliveryId }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="外部订单号" prop="externalOrderNo" min-width="180" />
            <el-table-column label="接入应用" prop="integrationName" min-width="160" />
            <el-table-column label="方式" prop="requestMethod" width="80" align="center" />
            <el-table-column label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="callbackStatusType(row.status)">
                  {{ callbackStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="尝试" prop="attemptCount" width="75" align="center" />
            <el-table-column label="HTTP" prop="lastHttpStatus" width="80" align="center" />
            <el-table-column label="严格 ACK" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.strictAcknowledged ? 'success' : 'info'">
                  {{ row.strictAcknowledged ? '通过' : '未通过' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="下次执行" width="190">
              <template #default="{ row }">{{ formatApiTime(row.nextAttemptAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="150" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="canRetryProtocolCallback(row as ProtocolCallbackVO)"
                  v-hasPermi="['payment:protocol-callback:retry']"
                  link
                  type="warning"
                  @click="retryCallback(row as ProtocolCallbackVO)"
                >
                  重试
                </el-button>
                <el-button
                  v-hasPermi="['payment:protocol-callback:retry']"
                  link
                  type="primary"
                  @click="replayCallback(row as ProtocolCallbackVO)"
                >
                  重放
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="callbackTotal > 0"
            v-model:page="callbackQuery.pageNum"
            v-model:limit="callbackQuery.pageSize"
            :total="callbackTotal"
            @pagination="loadCallbacks"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog
      v-model="applicationEditor.visible"
      :title="applicationEditor.id ? '编辑接入应用' : '新建接入应用'"
      width="680px"
      destroy-on-close
    >
      <el-form
        ref="applicationFormRef"
        :model="applicationEditor.form"
        :rules="applicationRules"
        label-width="130px"
      >
        <el-form-item v-if="showMerchantColumn" label="目标商户" required>
          <payment-merchant-target-select
            v-model="applicationEditor.form.merchantId"
            :disabled="Boolean(applicationEditor.id)"
            active-only
          />
        </el-form-item>
        <el-form-item label="应用编码" prop="integrationCode">
          <el-input
            v-model="applicationEditor.form.integrationCode"
            maxlength="64"
            placeholder="例如 mall_prod"
          />
        </el-form-item>
        <el-form-item label="应用名称" prop="integrationName">
          <el-input v-model="applicationEditor.form.integrationName" maxlength="100" />
        </el-form-item>
        <el-form-item label="订单有效期" prop="defaultExpireSeconds">
          <el-input-number
            v-model="applicationEditor.form.defaultExpireSeconds"
            :min="30"
            :max="3600"
          />
          <span class="form-tip">秒</span>
        </el-form-item>
        <el-form-item label="异步通知方式" prop="notifyMethod">
          <el-radio-group v-model="applicationEditor.form.notifyMethod">
            <el-radio-button value="GET">GET</el-radio-button>
            <el-radio-button value="POST">POST Form</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="确认策略" prop="callbackPolicy">
          <el-select v-model="applicationEditor.form.callbackPolicy" style="width: 100%">
            <el-option label="通知匹配后确认（默认）" value="NOTIFICATION_MATCHED" />
            <el-option label="人工确认后通知" value="MANUAL_CONFIRMED" />
            <el-option label="完成对账后通知" value="RECONCILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="回调域名白名单" required>
          <el-input
            v-model="applicationEditor.callbackHostsText"
            type="textarea"
            :rows="4"
            placeholder="每行一个域名，例如 pay.example.com；不填写协议和路径"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="applicationEditor.form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applicationEditor.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="applicationEditor.submitting"
          @click="saveApplication"
        >
          {{ applicationEditor.id ? '保存修改' : '创建并生成 Key' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="secretDialog.visible" title="易支付 Key（仅显示一次）" width="680px">
      <el-alert
        title="请立即复制并存入服务端密钥管理系统。关闭窗口后无法再次查看本次明文 Key。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-descriptions :column="2" border class="mt-3">
        <el-descriptions-item label="应用">{{ secretDialog.applicationName }}</el-descriptions-item>
        <el-descriptions-item label="PID">{{ secretDialog.pid }}</el-descriptions-item>
      </el-descriptions>
      <el-input v-model="secretDialog.apiKey" readonly class="mt-3">
        <template #append>
          <el-button icon="CopyDocument" @click="copyValue(secretDialog.apiKey)">复制</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" @click="secretDialog.visible = false">我已安全保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="applicationDetailVisible" title="接入应用详情" size="760px">
      <template v-if="applicationDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="应用名称">{{ applicationDetail.integrationName }}</el-descriptions-item>
          <el-descriptions-item label="应用编码">{{ applicationDetail.integrationCode }}</el-descriptions-item>
          <el-descriptions-item label="PID">{{ applicationDetail.pid }}</el-descriptions-item>
          <el-descriptions-item label="协议">{{ applicationDetail.profile }}</el-descriptions-item>
          <el-descriptions-item label="确认策略">
            {{ callbackPolicyLabel(applicationDetail.callbackPolicy) }}
          </el-descriptions-item>
          <el-descriptions-item label="通知方式">{{ applicationDetail.notifyMethod }}</el-descriptions-item>
          <el-descriptions-item label="回调白名单" :span="2">
            {{ applicationDetail.allowedCallbackHosts.join('、') }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">
            {{ applicationDetail.remark || '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <h4 class="section-title">密钥版本</h4>
        <el-table :data="applicationDetail.secrets || []" border>
          <el-table-column label="版本" width="90" align="center">
            <template #default="{ row }">v{{ row.secretVersion }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.status === 'ACTIVE' ? 'success' : row.status === 'REVOKED' ? 'danger' : 'info'"
              >
                {{ secretStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="启用时间" min-width="180">
            <template #default="{ row }">{{ formatApiTime(row.activatedAt) }}</template>
          </el-table-column>
          <el-table-column label="停用时间" min-width="180">
            <template #default="{ row }">
              {{ formatApiTime(row.revokedAt || row.retiredAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'RETIRED'"
                v-hasPermi="['payment:integration:secret']"
                link
                type="danger"
                @click="revokeSecret(applicationDetail!, row.id)"
              >
                撤销
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-drawer v-model="orderDetailVisible" title="外部订单详情" size="760px">
      <template v-if="orderDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="接入应用">{{ orderDetail.integrationName }}</el-descriptions-item>
          <el-descriptions-item label="平台">
            {{ orderDetail.platform === 'WECHAT' ? '微信' : '支付宝' }}
          </el-descriptions-item>
          <el-descriptions-item label="外部订单号" :span="2">
            {{ orderDetail.externalOrderNo }}
          </el-descriptions-item>
          <el-descriptions-item label="网关订单号" :span="2">
            {{ orderDetail.gatewayTradeNo }}
          </el-descriptions-item>
          <el-descriptions-item label="内部订单号" :span="2">
            {{ orderDetail.internalOrderNo || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="请求金额">
            {{ formatAmount(orderDetail.requestAmountMinor) }}
          </el-descriptions-item>
          <el-descriptions-item label="实付金额">
            {{ formatAmount(orderDetail.payableAmountMinor) }}
          </el-descriptions-item>
          <el-descriptions-item label="订单状态">{{ orderDetail.orderStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="确认等级">
            {{ confirmationStatusLabel(orderDetail.confirmationStatus) }}
          </el-descriptions-item>
          <el-descriptions-item label="确认策略">
            {{ callbackPolicyLabel(orderDetail.callbackPolicy) }}
          </el-descriptions-item>
          <el-descriptions-item label="回调状态">
            {{ callbackStatusLabel(orderDetail.callbackStatus) }}
          </el-descriptions-item>
          <el-descriptions-item label="风险状态" :span="2">
            <el-tag :type="orderDetail.riskStatus === 'NORMAL' ? 'success' : 'danger'">
              {{ orderDetail.riskStatus }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="orderDetail.riskReason" label="风险说明" :span="2">
            {{ orderDetail.riskReason }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatApiTime(orderDetail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatApiTime(orderDetail.paidAt) }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-drawer v-model="callbackDetailVisible" title="协议回调详情" size="860px">
      <template v-if="callbackDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Delivery ID" :span="2">
            {{ callbackDetail.deliveryId }}
          </el-descriptions-item>
          <el-descriptions-item label="Event ID" :span="2">
            {{ callbackDetail.eventId }}
          </el-descriptions-item>
          <el-descriptions-item label="外部订单号">
            {{ callbackDetail.externalOrderNo || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="网关订单号">
            {{ callbackDetail.gatewayTradeNo || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="callbackStatusType(callbackDetail.status)">
              {{ callbackStatusLabel(callbackDetail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="严格 ACK">
            {{ callbackDetail.strictAcknowledged ? 'HTTP 2xx 且响应 success' : '未通过' }}
          </el-descriptions-item>
          <el-descriptions-item label="请求方式">{{ callbackDetail.requestMethod }}</el-descriptions-item>
          <el-descriptions-item label="尝试次数">{{ callbackDetail.attemptCount }}</el-descriptions-item>
          <el-descriptions-item label="回调地址" :span="2">
            {{ callbackDetail.targetUrl }}
          </el-descriptions-item>
          <el-descriptions-item label="最后响应" :span="2">
            {{ callbackDetail.lastResponse || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="最后错误" :span="2">
            {{ callbackDetail.lastError || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="callbackDetail.replayOfId" label="重放来源">
            {{ callbackDetail.replayOfId }}
          </el-descriptions-item>
          <el-descriptions-item v-if="callbackDetail.replayReason" label="重放原因">
            {{ callbackDetail.replayReason }}
          </el-descriptions-item>
        </el-descriptions>
        <h4 class="section-title">投递尝试</h4>
        <el-table :data="callbackDetail.deliveryLogs || []" border>
          <el-table-column label="次数" prop="attemptNumber" width="70" align="center" />
          <el-table-column label="请求时间" width="190">
            <template #default="{ row }">{{ formatApiTime(row.requestAt) }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="100" align="right">
            <template #default="{ row }">
              {{ row.durationMs == null ? '-' : `${row.durationMs} ms` }}
            </template>
          </el-table-column>
          <el-table-column label="HTTP" prop="httpStatus" width="80" align="center" />
          <el-table-column label="ACK" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.acknowledged ? 'success' : 'danger'">
                {{ row.acknowledged ? '通过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="响应或错误" min-width="250" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.errorMessage || row.responseExcerpt || '-' }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup name="PaymentIntegration" lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules, TabsPaneContext } from 'element-plus';
import {
  createPaymentIntegration,
  getExternalOrder,
  getPaymentIntegration,
  getProtocolCallback,
  listEnabledQrAssets,
  listExternalOrders,
  listPaymentIntegrationRoutes,
  listPaymentIntegrations,
  listProtocolCallbacks,
  replayProtocolCallback,
  retryProtocolCallback,
  revokePaymentIntegrationSecret,
  rotatePaymentIntegrationSecret,
  savePaymentIntegrationRoutes,
  updatePaymentIntegration,
  updatePaymentIntegrationStatus
} from '@/api/payment';
import { formatApiTime } from '@/api/payment/time';
import type {
  ExternalOrderQuery,
  ExternalOrderVO,
  PaymentIntegrationForm,
  PaymentIntegrationQuery,
  PaymentIntegrationRouteForm,
  PaymentIntegrationVO,
  ProtocolCallbackQuery,
  ProtocolCallbackVO,
  QrAssetVO
} from '@/api/payment/types';
import PaymentMerchantTargetSelect from '@/components/PaymentMerchantTargetSelect/index.vue';
import { usePaymentMerchantScope } from '@/hooks/payment/useMerchantScope';
import { requestPaymentStepUp } from '@/utils/payment-step-up';
import {
  EPAY_STEP_UP_OPERATION,
  callbackPolicyLabel,
  callbackStatusLabel,
  callbackStatusType,
  canRetryProtocolCallback,
  confirmationStatusLabel,
  splitCallbackHosts,
  toRouteForm
} from './integration-state';

const activeTab = ref('applications');
const integrationOptions = ref<PaymentIntegrationVO[]>([]);
const {
  merchantStore,
  showMerchantColumn,
  defaultTargetMerchantId,
  watchScope
} = usePaymentMerchantScope();

const applicationQueryRef = ref<FormInstance>();
const applicationQuery = reactive<PaymentIntegrationQuery>({
  pageNum: 1,
  pageSize: 10,
  integrationName: '',
  pid: '',
  status: ''
});
const applicationLoading = ref(false);
const applicationTotal = ref(0);
const applications = ref<PaymentIntegrationVO[]>([]);

const emptyApplicationForm = (): PaymentIntegrationForm => ({
  integrationCode: '',
  integrationName: '',
  defaultExpireSeconds: 300,
  notifyMethod: 'GET',
  callbackPolicy: 'NOTIFICATION_MATCHED',
  allowedCallbackHosts: [],
  remark: ''
});
const applicationFormRef = ref<FormInstance>();
const applicationRules: FormRules<PaymentIntegrationForm> = {
  integrationCode: [
    { required: true, message: '请输入应用编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]{2,64}$/,
      message: '仅支持 2 至 64 位字母、数字、下划线和短横线',
      trigger: 'blur'
    }
  ],
  integrationName: [
    { required: true, message: '请输入应用名称', trigger: 'blur' },
    { max: 100, message: '应用名称不能超过 100 个字符', trigger: 'blur' }
  ],
  defaultExpireSeconds: [{ required: true, message: '请输入订单有效期', trigger: 'change' }],
  notifyMethod: [{ required: true, message: '请选择异步通知方式', trigger: 'change' }],
  callbackPolicy: [{ required: true, message: '请选择确认策略', trigger: 'change' }]
};
const applicationEditor = reactive({
  visible: false,
  submitting: false,
  id: undefined as string | number | undefined,
  callbackHostsText: '',
  form: emptyApplicationForm()
});
const secretDialog = reactive({
  visible: false,
  applicationName: '',
  pid: '',
  apiKey: ''
});
const applicationDetailVisible = ref(false);
const applicationDetail = ref<PaymentIntegrationVO>();

const selectedIntegrationId = ref<string | number>();
const routeLoading = ref(false);
const routeSaving = ref(false);
const routeForms = ref<PaymentIntegrationRouteForm[]>([]);
const qrAssets = ref<QrAssetVO[]>([]);

const orderQueryRef = ref<FormInstance>();
const orderQuery = reactive<ExternalOrderQuery>({
  pageNum: 1,
  pageSize: 10,
  integrationId: undefined,
  externalOrderNo: '',
  gatewayTradeNo: '',
  riskStatus: ''
});
const orderLoading = ref(false);
const orderTotal = ref(0);
const orders = ref<ExternalOrderVO[]>([]);
const orderDetailVisible = ref(false);
const orderDetail = ref<ExternalOrderVO>();

const callbackQueryRef = ref<FormInstance>();
const callbackQuery = reactive<ProtocolCallbackQuery>({
  pageNum: 1,
  pageSize: 10,
  integrationId: undefined,
  deliveryId: '',
  status: ''
});
const callbackLoading = ref(false);
const callbackTotal = ref(0);
const callbacks = ref<ProtocolCallbackVO[]>([]);
const callbackDetailVisible = ref(false);
const callbackDetail = ref<ProtocolCallbackVO>();

const loadIntegrationOptions = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  const response = await listPaymentIntegrations({ pageNum: 1, pageSize: 1000 });
  if (scopeVersion !== merchantStore.scopeVersion) return;
  integrationOptions.value = response.data?.rows || [];
  if (
    selectedIntegrationId.value &&
    !integrationOptions.value.some(item => String(item.id) === String(selectedIntegrationId.value))
  ) {
    selectedIntegrationId.value = undefined;
    routeForms.value = [];
  }
};

const loadApplications = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  applicationLoading.value = true;
  try {
    const response = await listPaymentIntegrations(applicationQuery);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    applications.value = response.data?.rows || [];
    applicationTotal.value = response.data?.total || 0;
  } finally {
    applicationLoading.value = false;
  }
};

const searchApplications = () => {
  applicationQuery.pageNum = 1;
  loadApplications();
};

const resetApplicationQuery = () => {
  applicationQueryRef.value?.resetFields();
  searchApplications();
};

const openCreateApplication = () => {
  applicationEditor.id = undefined;
  applicationEditor.form = emptyApplicationForm();
  applicationEditor.form.merchantId = defaultTargetMerchantId();
  applicationEditor.callbackHostsText = '';
  applicationEditor.visible = true;
  nextTick(() => applicationFormRef.value?.clearValidate());
};

const openEditApplication = (row: PaymentIntegrationVO) => {
  applicationEditor.id = row.id;
  applicationEditor.form = {
    merchantId: row.merchantId,
    integrationCode: row.integrationCode,
    integrationName: row.integrationName,
    defaultExpireSeconds: row.defaultExpireSeconds,
    notifyMethod: row.notifyMethod,
    callbackPolicy: row.callbackPolicy,
    allowedCallbackHosts: [...row.allowedCallbackHosts],
    remark: row.remark || ''
  };
  applicationEditor.callbackHostsText = row.allowedCallbackHosts.join('\n');
  applicationEditor.visible = true;
  nextTick(() => applicationFormRef.value?.clearValidate());
};

const saveApplication = async () => {
  await applicationFormRef.value?.validate();
  if (!applicationEditor.id && !applicationEditor.form.merchantId) {
    ElMessage.warning('请选择目标商户');
    return;
  }
  const callbackHosts = splitCallbackHosts(applicationEditor.callbackHostsText);
  if (!callbackHosts.length) {
    ElMessage.warning('请至少配置一个回调域名白名单');
    return;
  }
  applicationEditor.submitting = true;
  try {
    const data: PaymentIntegrationForm = {
      ...applicationEditor.form,
      integrationCode: applicationEditor.form.integrationCode.trim(),
      integrationName: applicationEditor.form.integrationName.trim(),
      allowedCallbackHosts: callbackHosts,
      remark: applicationEditor.form.remark?.trim()
    };
    const token = await requestPaymentStepUp(
      EPAY_STEP_UP_OPERATION,
      applicationEditor.id ? '修改支付接入' : '创建支付接入'
    );
    if (applicationEditor.id) {
      await updatePaymentIntegration(applicationEditor.id, data, token);
      ElMessage.success('接入应用已更新');
    } else {
      const created = (await createPaymentIntegration(data, token)).data;
      if (!created?.integration || !created.apiKey) {
        throw new Error('创建响应缺少应用或一次性 Key');
      }
      showSecret(created.integration, created.apiKey);
      selectedIntegrationId.value = created.integration.id;
      ElMessage.success('接入应用已创建');
    }
    applicationEditor.visible = false;
    await Promise.all([loadApplications(), loadIntegrationOptions()]);
  } finally {
    applicationEditor.submitting = false;
  }
};

const openApplicationDetail = async (row: PaymentIntegrationVO) => {
  applicationDetail.value = (await getPaymentIntegration(row.id)).data;
  applicationDetailVisible.value = true;
};

const toggleApplication = async (row: PaymentIntegrationVO) => {
  const nextStatus = row.status === '0' ? '1' : '0';
  await ElMessageBox.confirm(
    `确认${nextStatus === '0' ? '启用' : '停用'}商户“${row.merchantName || row.merchantCode || row.merchantId}”的接入“${row.integrationName}”吗？`,
    '接入状态确认',
    { type: nextStatus === '0' ? 'success' : 'warning' }
  );
  const token = await requestPaymentStepUp(EPAY_STEP_UP_OPERATION, '变更支付接入状态');
  await updatePaymentIntegrationStatus(row.id, nextStatus, token);
  ElMessage.success(nextStatus === '0' ? '接入应用已启用' : '接入应用已停用');
  await Promise.all([loadApplications(), loadIntegrationOptions()]);
};

const rotateSecret = async (row: PaymentIntegrationVO) => {
  await ElMessageBox.confirm(
    `确认轮换商户“${row.merchantName || row.merchantCode || row.merchantId}”的接入“${row.integrationName}”密钥吗？轮换后旧 Key 进入保留状态，既有订单仍使用创建时的密钥版本签名，新订单使用新 Key。`,
    '轮换易支付 Key',
    { type: 'warning' }
  );
  const token = await requestPaymentStepUp(EPAY_STEP_UP_OPERATION, '轮换易支付 Key');
  const result = (await rotatePaymentIntegrationSecret(row.id, token)).data;
  if (!result?.integration || !result.apiKey) {
    throw new Error('轮换响应缺少一次性 Key');
  }
  showSecret(result.integration, result.apiKey);
  await Promise.all([loadApplications(), loadIntegrationOptions()]);
};

const revokeSecret = async (integration: PaymentIntegrationVO, secretId: string | number) => {
  await ElMessageBox.confirm(
    `确认撤销商户“${integration.merchantName || integration.merchantCode || integration.merchantId}”的接入“${integration.integrationName}”历史 Key 吗？撤销后，仍引用该版本的历史订单将无法重新签名通知。`,
    '撤销历史 Key',
    { type: 'warning' }
  );
  const token = await requestPaymentStepUp(EPAY_STEP_UP_OPERATION, '撤销易支付 Key');
  const updated = (
    await revokePaymentIntegrationSecret(integration.id, secretId, token)
  ).data;
  applicationDetail.value = updated;
  ElMessage.success('历史 Key 已撤销');
  await loadApplications();
};

const showSecret = (integration: PaymentIntegrationVO, apiKey: string) => {
  secretDialog.applicationName = integration.integrationName;
  secretDialog.pid = integration.pid;
  secretDialog.apiKey = apiKey;
  secretDialog.visible = true;
};

const copyValue = async (value: string) => {
  await navigator.clipboard.writeText(value);
  ElMessage.success('已复制');
};

const secretStatusLabel = (status: string) =>
  ({ ACTIVE: '使用中', RETIRED: '已轮换', REVOKED: '已撤销' })[status] || status;

const openRoutes = async (row: PaymentIntegrationVO) => {
  selectedIntegrationId.value = row.id;
  activeTab.value = 'routes';
  await loadRoutes();
};

const loadRoutes = async () => {
  if (!selectedIntegrationId.value) {
    routeForms.value = [];
    return;
  }
  routeLoading.value = true;
  try {
    const integration = integrationOptions.value.find(
      item => String(item.id) === String(selectedIntegrationId.value)
    );
    const [routesResponse, assetsResponse] = await Promise.all([
      listPaymentIntegrationRoutes(selectedIntegrationId.value),
      listEnabledQrAssets(undefined, integration?.merchantId)
    ]);
    routeForms.value = (routesResponse.data || []).map(toRouteForm);
    qrAssets.value = assetsResponse.data || [];
  } finally {
    routeLoading.value = false;
  }
};

const qrAssetsForPlatform = (platform: 'WECHAT' | 'ALIPAY') =>
  qrAssets.value.filter(asset => asset.platform === platform && asset.status === '0');

const addRoute = (platform: 'WECHAT' | 'ALIPAY') => {
  const options = qrAssetsForPlatform(platform);
  if (!options.length) {
    ElMessage.warning(`当前没有已启用的${platform === 'WECHAT' ? '微信' : '支付宝'}二维码`);
    return;
  }
  routeForms.value.push({
    payType: platform === 'WECHAT' ? 'wxpay' : 'alipay',
    platform,
    qrAssetId: options[0].id,
    priority: (routeForms.value.filter(item => item.platform === platform).length + 1) * 100,
    status: '0'
  });
};

const removeRoute = (index: number) => routeForms.value.splice(index, 1);

const saveRoutes = async () => {
  if (!selectedIntegrationId.value) return;
  if (routeForms.value.some(item => !item.qrAssetId)) {
    ElMessage.warning('请选择每条路由对应的收款二维码');
    return;
  }
  routeSaving.value = true;
  try {
    const token = await requestPaymentStepUp(EPAY_STEP_UP_OPERATION, '保存易支付路由');
    const response = await savePaymentIntegrationRoutes(
      selectedIntegrationId.value,
      routeForms.value,
      token
    );
    routeForms.value = (response.data || []).map(toRouteForm);
    ElMessage.success('支付路由已保存');
  } finally {
    routeSaving.value = false;
  }
};

const loadOrders = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  orderLoading.value = true;
  try {
    const response = await listExternalOrders(orderQuery);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    orders.value = response.data?.rows || [];
    orderTotal.value = response.data?.total || 0;
  } finally {
    orderLoading.value = false;
  }
};

const searchOrders = () => {
  orderQuery.pageNum = 1;
  loadOrders();
};

const resetOrderQuery = () => {
  orderQueryRef.value?.resetFields();
  searchOrders();
};

const openOrderDetail = async (id: string | number) => {
  orderDetail.value = (await getExternalOrder(id)).data;
  orderDetailVisible.value = true;
};

const loadCallbacks = async () => {
  const scopeVersion = merchantStore.scopeVersion;
  callbackLoading.value = true;
  try {
    const response = await listProtocolCallbacks(callbackQuery);
    if (scopeVersion !== merchantStore.scopeVersion) return;
    callbacks.value = response.data?.rows || [];
    callbackTotal.value = response.data?.total || 0;
  } finally {
    callbackLoading.value = false;
  }
};

const searchCallbacks = () => {
  callbackQuery.pageNum = 1;
  loadCallbacks();
};

const resetCallbackQuery = () => {
  callbackQueryRef.value?.resetFields();
  searchCallbacks();
};

const openCallbackDetail = async (id: string | number) => {
  callbackDetail.value = (await getProtocolCallback(id)).data;
  callbackDetailVisible.value = true;
};

const retryCallback = async (row: ProtocolCallbackVO) => {
  await ElMessageBox.confirm(
    `确认重试商户“${row.merchantName || row.merchantCode || row.merchantId}”的协议回调 ${row.deliveryId} 吗？该操作会将已停止重试的回调重新放入自动投递队列。`,
    '重试协议回调',
    { type: 'warning' }
  );
  const token = await requestPaymentStepUp(EPAY_STEP_UP_OPERATION, '重试易支付回调');
  await retryProtocolCallback(row.id, token);
  ElMessage.success('回调已重新入队');
  await loadCallbacks();
};

const replayCallback = async (row: ProtocolCallbackVO) => {
  const prompt = await ElMessageBox.prompt(
    `商户“${row.merchantName || row.merchantCode || row.merchantId}”的回调 ${row.deliveryId}：重放会创建新的 Delivery ID，请填写重放原因。`,
    '人工重放协议回调',
    {
      inputType: 'textarea',
      inputPattern: /\S+/,
      inputErrorMessage: '请输入重放原因',
      inputValidator: value => (value.trim().length <= 500 ? true : '原因不能超过 500 个字符')
    }
  );
  const token = await requestPaymentStepUp(EPAY_STEP_UP_OPERATION, '重放易支付回调');
  await replayProtocolCallback(row.id, prompt.value.trim(), token);
  ElMessage.success('已创建新的回调投递任务');
  await loadCallbacks();
};

const formatAmount = (minor?: number) =>
  minor == null ? '-' : `¥${(minor / 100).toFixed(2)}`;

const handleTabChange = async (name: string | number | TabsPaneContext) => {
  const value = typeof name === 'object' ? name.paneName : name;
  if (value === 'routes' && selectedIntegrationId.value) await loadRoutes();
  if (value === 'orders') await loadOrders();
  if (value === 'callbacks') await loadCallbacks();
};

const handleMerchantChanged = async () => {
  applicationQuery.pageNum = 1;
  orderQuery.pageNum = 1;
  callbackQuery.pageNum = 1;
  selectedIntegrationId.value = undefined;
  routeForms.value = [];
  applicationEditor.visible = false;
  applicationDetailVisible.value = false;
  orderDetailVisible.value = false;
  callbackDetailVisible.value = false;
  secretDialog.visible = false;
  await Promise.all([loadApplications(), loadIntegrationOptions()]);
  if (activeTab.value === 'orders') await loadOrders();
  if (activeTab.value === 'callbacks') await loadCallbacks();
};

watchScope(handleMerchantChanged);
onMounted(async () => {
  await Promise.all([loadApplications(), loadIntegrationOptions()]);
});
</script>

<style scoped lang="scss">
.payment-integration-page {
  min-width: 0;
}

.table-toolbar,
.route-heading,
.route-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.table-toolbar,
.route-heading {
  margin-bottom: 16px;
}

.table-toolbar h3,
.route-heading h3 {
  margin: 0 0 5px;
}

.table-toolbar p,
.route-heading p,
.muted,
.form-tip {
  color: var(--el-text-color-secondary);
}

.table-toolbar p,
.route-heading p,
.muted {
  margin: 0;
  font-size: 13px;
}

.route-actions {
  margin-top: 16px;
}

.form-tip {
  margin-left: 8px;
}

.section-title {
  margin: 24px 0 12px;
}

@media (max-width: 900px) {
  .table-toolbar,
  .route-heading,
  .route-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
