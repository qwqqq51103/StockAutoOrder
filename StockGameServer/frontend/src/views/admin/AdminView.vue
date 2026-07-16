<script setup>
import { computed, onMounted, reactive } from "vue";
import { ElMessage } from "element-plus";
import { useAdminStore } from "@/stores/admin";
import { useAuthStore } from "@/stores/auth";
import { useMarketStore } from "@/stores/market";
import {
  formatDateTime,
  formatMoney,
  formatPercent,
  formatPrice,
  formatSignedMoney,
  roleLabel
} from "@/services/formatters";

const authStore = useAuthStore();
const marketStore = useMarketStore();
const adminStore = useAdminStore();

const simulatorForm = reactive({
  retailCount: 0,
  noiseCount: 0
});

const announcementForm = reactive({
  message: ""
});

const topBids = computed(() => marketStore.orderBook?.bids?.slice(0, 5) || []);
const topAsks = computed(() => marketStore.orderBook?.asks?.slice(0, 5) || []);

async function refreshAdmin() {
  await adminStore.bootstrap();
  if (adminStore.status) {
    simulatorForm.retailCount = adminStore.status.aiRetailCount;
    simulatorForm.noiseCount = adminStore.status.aiNoiseCount;
  }
}

async function runAction(action, successMessage) {
  try {
    await action();
    ElMessage.success(successMessage);
  } catch (error) {
    ElMessage.error(error.message);
  }
}

function toggleUser(row) {
  return runAction(
    () => adminStore.setUserEnabled(row.username, !row.enabled),
    `${row.username} 已${row.enabled ? "停用" : "啟用"}。`
  );
}

function resetAccount(row) {
  return runAction(
    () => adminStore.resetAccount(row.username),
    `${row.username} 的帳戶已重置。`
  );
}

function updateSimulator() {
  return runAction(
    () => adminStore.updateSimulatorConfig({
      retailCount: Number(simulatorForm.retailCount),
      noiseCount: Number(simulatorForm.noiseCount)
    }),
    "模擬器設定已更新。"
  );
}

function sendAnnouncement() {
  return runAction(
    async () => {
      await adminStore.sendAnnouncement(announcementForm.message);
      announcementForm.message = "";
    },
    "公告已送出。"
  );
}

onMounted(async () => {
  if (!authStore.isAdmin) return;
  await refreshAdmin();
});
</script>

<template>
  <section class="space-y-6">
    <el-result
      v-if="!authStore.isAuthenticated"
      icon="warning"
      title="需要後台登入"
      sub-title="請使用 ADMIN 角色帳號登入後操作市場控制功能。"
    />

    <el-result
      v-else-if="!authStore.isAdmin"
      icon="error"
      title="權限不足"
      sub-title="此頁面僅限 ADMIN 使用者存取。"
    />

    <template v-else>
      <el-alert
        title="後台控制台已啟用"
        type="success"
        :closable="false"
        description="此頁已串接真實後台 API，可控制市場、調整模擬器、發送公告、啟用或停用使用者，以及重置玩家帳戶。"
        show-icon
      />

      <el-row :gutter="16">
        <el-col :xs="24" :md="8">
          <el-card shadow="never">
            <template #header>
              <div class="flex items-center justify-between">
                <span>市場狀態</span>
                <el-tag :type="adminStore.status?.marketOpen ? 'success' : 'danger'">
                  {{ adminStore.status?.marketOpen ? "開盤" : "休市" }}
                </el-tag>
              </div>
            </template>

            <el-descriptions :column="1" border>
              <el-descriptions-item label="股票代號">
                {{ adminStore.status?.symbol || marketStore.quote?.symbol || "--" }}
              </el-descriptions-item>
              <el-descriptions-item label="最新價">
                {{ adminStore.status ? formatPrice(adminStore.status.lastPrice) : "--" }}
              </el-descriptions-item>
              <el-descriptions-item label="最佳買 / 賣">
                {{ adminStore.status ? `${formatPrice(adminStore.status.bestBid)} / ${formatPrice(adminStore.status.bestAsk)}` : "--" }}
              </el-descriptions-item>
              <el-descriptions-item label="成交量">
                {{ adminStore.status?.totalVolume?.toLocaleString("zh-TW") || "--" }}
              </el-descriptions-item>
              <el-descriptions-item label="Tick 間隔">
                {{ adminStore.status ? `${adminStore.status.tickIntervalMs} ms` : "--" }}
              </el-descriptions-item>
              <el-descriptions-item label="帳戶數">
                {{ adminStore.status?.accountCount || 0 }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="8">
          <el-card shadow="never">
            <template #header>
              <span>市場控制</span>
            </template>

            <div class="grid gap-3">
              <el-button
                type="success"
                :loading="adminStore.loading"
                @click="runAction(() => adminStore.setMarket(true), '市場已開盤。')"
              >
                開盤
              </el-button>
              <el-button
                type="danger"
                plain
                :loading="adminStore.loading"
                @click="runAction(() => adminStore.setMarket(false), '市場已休市。')"
              >
                休市
              </el-button>
              <el-button
                type="primary"
                plain
                :loading="adminStore.loading"
                @click="runAction(() => adminStore.triggerTick(), '已執行手動 tick。')"
              >
                執行手動 Tick
              </el-button>
              <el-button plain :loading="adminStore.loading" @click="runAction(refreshAdmin, '後台資料已重新整理。')">
                重新整理
              </el-button>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="8">
          <el-card shadow="never">
            <template #header>
              <span>模擬器設定</span>
            </template>

            <el-form label-position="top">
              <el-form-item label="散戶 AI 數量">
                <el-input-number v-model="simulatorForm.retailCount" :min="0" :max="50" class="!w-full" />
              </el-form-item>
              <el-form-item label="雜訊交易 AI 數量">
                <el-input-number v-model="simulatorForm.noiseCount" :min="0" :max="20" class="!w-full" />
              </el-form-item>
              <el-button type="primary" :loading="adminStore.loading" @click="updateSimulator">
                儲存設定
              </el-button>
            </el-form>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :lg="14">
          <el-card shadow="never">
            <template #header>
              <div class="flex items-center justify-between">
                <span>廣播公告</span>
                <el-tag type="info">{{ marketStore.latestAnnouncement || "尚無近期公告" }}</el-tag>
              </div>
            </template>

            <el-form label-position="top">
              <el-form-item label="公告內容">
                <el-input
                  v-model="announcementForm.message"
                  type="textarea"
                  :rows="4"
                  maxlength="200"
                  show-word-limit
                  placeholder="輸入要廣播給所有連線玩家的簡短訊息。"
                />
              </el-form-item>
              <el-button
                type="primary"
                :loading="adminStore.loading"
                :disabled="!announcementForm.message.trim()"
                @click="sendAnnouncement"
              >
                發送公告
              </el-button>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="10">
          <el-card shadow="never">
            <template #header>
              <span>最佳五檔</span>
            </template>
            <el-tabs>
              <el-tab-pane label="買盤">
                <el-table :data="topBids" size="small">
                  <el-table-column prop="price" label="價格">
                    <template #default="{ row }">{{ formatPrice(row.price) }}</template>
                  </el-table-column>
                  <el-table-column prop="quantity" label="數量" />
                  <el-table-column prop="orderCount" label="筆數" />
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="賣盤">
                <el-table :data="topAsks" size="small">
                  <el-table-column prop="price" label="價格">
                    <template #default="{ row }">{{ formatPrice(row.price) }}</template>
                  </el-table-column>
                  <el-table-column prop="quantity" label="數量" />
                  <el-table-column prop="orderCount" label="筆數" />
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :lg="12">
          <el-card shadow="never">
            <template #header>
              <span>近期成交</span>
            </template>
            <el-table :data="marketStore.recentTrades.slice(0, 15)" size="small">
              <el-table-column prop="executedAt" label="時間" min-width="180">
                <template #default="{ row }">{{ formatDateTime(row.executedAt) }}</template>
              </el-table-column>
              <el-table-column prop="price" label="價格">
                <template #default="{ row }">{{ formatPrice(row.price) }}</template>
              </el-table-column>
              <el-table-column prop="quantity" label="數量" />
              <el-table-column prop="buyerInitiated" label="主動方">
                <template #default="{ row }">{{ row.buyerInitiated ? "買方" : "賣方" }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card shadow="never">
            <template #header>
              <span>排行榜</span>
            </template>
            <el-table :data="marketStore.leaderboard.slice(0, 10)" size="small">
              <el-table-column prop="rank" label="#" width="60" />
              <el-table-column prop="username" label="玩家" />
              <el-table-column prop="realizedPnl" label="已實現損益">
                <template #default="{ row }">{{ formatSignedMoney(row.realizedPnl) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never">
        <template #header>
          <div class="flex items-center justify-between">
            <span>玩家帳戶控制</span>
            <el-tag type="success">{{ authStore.username }}</el-tag>
          </div>
        </template>

        <el-table :data="adminStore.accounts" size="small">
          <el-table-column prop="username" label="玩家" min-width="130" />
          <el-table-column prop="role" label="角色" width="100">
            <template #default="{ row }">
              <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">{{ roleLabel(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="enabled" label="啟用" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'warning'">{{ row.enabled ? "是" : "否" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="availableCash" label="現金" min-width="120">
            <template #default="{ row }">{{ formatMoney(row.availableCash) }}</template>
          </el-table-column>
          <el-table-column prop="totalAssets" label="資產" min-width="120">
            <template #default="{ row }">{{ formatMoney(row.totalAssets) }}</template>
          </el-table-column>
          <el-table-column prop="totalStocks" label="股數" width="90" />
          <el-table-column prop="realizedPnl" label="已實現" min-width="120">
            <template #default="{ row }">{{ formatSignedMoney(row.realizedPnl) }}</template>
          </el-table-column>
          <el-table-column prop="unrealizedPnl" label="未實現" min-width="120">
            <template #default="{ row }">{{ formatSignedMoney(row.unrealizedPnl) }}</template>
          </el-table-column>
          <el-table-column prop="returnRate" label="報酬率" width="100">
            <template #default="{ row }">{{ formatPercent(row.returnRate) }}</template>
          </el-table-column>
          <el-table-column prop="lastLoginAt" label="最後登入" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" min-width="210">
            <template #default="{ row }">
              <div class="flex gap-2">
                <el-button
                  size="small"
                  :type="row.enabled ? 'warning' : 'success'"
                  :disabled="row.username === authStore.username"
                  @click="toggleUser(row)"
                >
                  {{ row.enabled ? "停用" : "啟用" }}
                </el-button>
                <el-button size="small" type="danger" plain @click="resetAccount(row)">
                  重置
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </section>
</template>
