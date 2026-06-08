<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  clearSession,
  deleteAlarm,
  fetchAlarmLogs,
  fetchAlarms,
  fetchAuditLogs,
  fetchUsers,
  hasStoredSession,
  login,
  setSessionTokens,
  updateUserStatus,
  type PageParams
} from "./api";

type TabKey = "users" | "alarms" | "logs" | "audits";

const pageSizeOptions = [10, 20, 50, 100];

const loginForm = reactive({
  account: "",
  isEmail: true,
  password: ""
});

const loading = ref(false);
const activeTab = ref<TabKey>("users");
const search = ref("");
const loggedIn = ref(hasStoredSession());

const users = ref<any[]>([]);
const alarms = ref<any[]>([]);
const logs = ref<any[]>([]);
const audits = ref<any[]>([]);

const totals = reactive<Record<TabKey, number>>({
  users: 0,
  alarms: 0,
  logs: 0,
  audits: 0
});

const paging = reactive<Record<TabKey, PageParams>>({
  users: { page: 1, pageSize: 20 },
  alarms: { page: 1, pageSize: 20 },
  logs: { page: 1, pageSize: 20 },
  audits: { page: 1, pageSize: 20 }
});

async function handleLogin() {
  loading.value = true;
  try {
    const response = await login(loginForm);
    setSessionTokens(response.accessToken, response.refreshToken);
    loggedIn.value = true;
    ElMessage.success(`已登录：${response.user.nickname || response.user.role}`);
    resetAllPages();
    await loadAll();
  } catch (error) {
    handleApiError(error);
  } finally {
    loading.value = false;
  }
}

function handleLogout() {
  clearSession();
  loggedIn.value = false;
  users.value = [];
  alarms.value = [];
  logs.value = [];
  audits.value = [];
  resetAllPages();
}

function resetAllPages() {
  (Object.keys(paging) as TabKey[]).forEach((tab) => {
    paging[tab].page = 1;
  });
}

function clearData() {
  users.value = [];
  alarms.value = [];
  logs.value = [];
  audits.value = [];
}

function handleApiError(error: unknown) {
  ElMessage.error((error as Error).message);
  if (!hasStoredSession()) {
    loggedIn.value = false;
    clearData();
    resetAllPages();
  }
}

async function loadAll() {
  loading.value = true;
  try {
    const [usersData, alarmsData, logsData, auditsData] = await Promise.all([
      fetchUsers(search.value.trim(), paging.users),
      fetchAlarms(search.value.trim(), paging.alarms),
      fetchAlarmLogs(paging.logs),
      fetchAuditLogs(paging.audits)
    ]);
    users.value = usersData.items;
    alarms.value = alarmsData.items;
    logs.value = logsData.items;
    audits.value = auditsData.items;
    totals.users = usersData.total;
    totals.alarms = alarmsData.total;
    totals.logs = logsData.total;
    totals.audits = auditsData.total;
  } catch (error) {
    handleApiError(error);
  } finally {
    loading.value = false;
  }
}

async function loadTab(tab: TabKey) {
  loading.value = true;
  try {
    if (tab === "users") {
      const data = await fetchUsers(search.value.trim(), paging.users);
      users.value = data.items;
      totals.users = data.total;
    } else if (tab === "alarms") {
      const data = await fetchAlarms(search.value.trim(), paging.alarms);
      alarms.value = data.items;
      totals.alarms = data.total;
    } else if (tab === "logs") {
      const data = await fetchAlarmLogs(paging.logs);
      logs.value = data.items;
      totals.logs = data.total;
    } else {
      const data = await fetchAuditLogs(paging.audits);
      audits.value = data.items;
      totals.audits = data.total;
    }
  } catch (error) {
    handleApiError(error);
  } finally {
    loading.value = false;
  }
}

async function handleUserToggle(user: any) {
  try {
    await updateUserStatus(user.id, user.status === 0 ? 1 : 0);
    await loadTab("users");
  } catch (error) {
    handleApiError(error);
  }
}

async function handleDeleteAlarm(row: any) {
  try {
    await deleteAlarm(row.id);
    ElMessage.success("闹钟已软删除");
    await loadTab("alarms");
  } catch (error) {
    handleApiError(error);
  }
}

async function handleSearch() {
  resetAllPages();
  await loadAll();
}

async function handleTabPageChange(tab: TabKey, page: number) {
  paging[tab].page = page;
  await loadTab(tab);
}

async function handleTabSizeChange(tab: TabKey, pageSize: number) {
  paging[tab].pageSize = pageSize;
  paging[tab].page = 1;
  await loadTab(tab);
}

function formatDateTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", { hour12: false });
}

function formatDuration(totalSeconds?: number | null) {
  const seconds = Math.max(totalSeconds || 0, 0);
  const hour = Math.floor(seconds / 3600);
  const minute = Math.floor((seconds % 3600) / 60);
  const second = seconds % 60;
  return [hour, minute, second].map((part) => String(part).padStart(2, "0")).join(":");
}

function alarmTypeLabel(type: number) {
  return (
    {
      1: "单次",
      2: "倒计时",
      3: "每周",
      4: "每月",
      5: "纪念日"
    }[type] || `类型 ${type}`
  );
}

function userStatusLabel(status: number) {
  return status === 0 ? "启用" : "禁用";
}

function alarmStatusLabel(status: number) {
  return status === 0 ? "正常" : "已删除";
}

function alarmActionLabel(action: number) {
  return (
    {
      1: "触发",
      2: "关闭",
      3: "稍后提醒"
    }[action] || `动作 ${action}`
  );
}

function weekdayLabels(bits: number) {
  const labels = ["一", "二", "三", "四", "五", "六", "日"];
  return labels.filter((_, index) => (bits & (1 << index)) !== 0).map((label) => `周${label}`);
}

function formatAlarmTime(row: any) {
  if (row.type === 2) {
    const duration = formatDuration(row.durationSec);
    const trigger = row.triggerTime ? `截止 ${formatDateTime(row.triggerTime)}` : "";
    return trigger ? `时长 ${duration} · ${trigger}` : `时长 ${duration}`;
  }
  return formatDateTime(row.triggerTime);
}

function formatAlarmCycle(row: any) {
  if (row.type === 1) return "一次性";
  if (row.type === 2) return "倒计时";
  if (row.type === 3) {
    if (row.scheduleMode === 1) return "法定工作日";
    if (row.repeatWeekdays === 127) return "每天";
    const days = weekdayLabels(row.repeatWeekdays || 0);
    return days.length > 0 ? days.join("、") : "每周";
  }
  if (row.type === 4) {
    return row.intervalMonths > 1 ? `每 ${row.intervalMonths} 个月` : "每月";
  }
  if (row.type === 5) {
    const calendar = row.anniversaryCalendar === 1 ? "农历" : "公历";
    const base = row.intervalYears > 1 ? `每 ${row.intervalYears} 年` : "每年";
    return `${base} · ${calendar}`;
  }
  return "-";
}

onMounted(async () => {
  if (loggedIn.value) {
    await loadAll();
  }
});
</script>

<template>
  <div class="page-shell">
    <div class="hero">
      <div>
        <p class="eyebrow">SmartClock 管理台</p>
        <h1>云端提醒管理后台</h1>
        <p class="subtitle">
          在这里统一查看用户、闹钟、提醒日志和后台审计记录。
        </p>
      </div>
      <div class="hero-actions">
        <el-button v-if="loggedIn" type="danger" plain @click="handleLogout">退出登录</el-button>
      </div>
    </div>

    <el-card v-if="!loggedIn" shadow="never" class="login-card">
      <el-form label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="账号">
          <el-input v-model="loginForm.account" placeholder="管理员邮箱或手机号" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="账号类型">
          <el-switch
            v-model="loginForm.isEmail"
            inline-prompt
            active-text="邮箱"
            inactive-text="手机"
          />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="handleLogin">
          登录后台
        </el-button>
      </el-form>
    </el-card>

    <template v-else>
      <div class="toolbar">
        <el-input
          v-model="search"
          placeholder="搜索用户、闹钟标题或 client_uuid"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
      </div>

      <el-tabs v-model="activeTab" class="tabs">
        <el-tab-pane label="用户" name="users">
          <div class="table-card">
            <el-table :data="users" stripe>
              <el-table-column prop="id" label="ID" width="90" />
              <el-table-column prop="phone" label="手机号" min-width="140" />
              <el-table-column prop="email" label="邮箱" min-width="180" />
              <el-table-column prop="nickname" label="昵称" min-width="140" />
              <el-table-column prop="role" label="角色" width="100" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 0 ? 'success' : 'danger'">
                    {{ userStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button size="small" @click="handleUserToggle(row)">
                    {{ row.status === 0 ? "禁用" : "启用" }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="pager">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next, jumper"
                :total="totals.users"
                :current-page="paging.users.page"
                :page-size="paging.users.pageSize"
                :page-sizes="pageSizeOptions"
                @current-change="(page) => handleTabPageChange('users', page)"
                @size-change="(size) => handleTabSizeChange('users', size)"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="闹钟" name="alarms">
          <div class="table-card">
            <el-table :data="alarms" stripe>
              <el-table-column prop="id" label="ID" width="90" />
              <el-table-column prop="clientUuid" label="客户端标识" width="280" />
              <el-table-column prop="title" label="名称" min-width="160" />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  {{ alarmTypeLabel(row.type) }}
                </template>
              </el-table-column>
              <el-table-column label="提醒时间" min-width="220">
                <template #default="{ row }">
                  {{ formatAlarmTime(row) }}
                </template>
              </el-table-column>
              <el-table-column label="周期" min-width="180">
                <template #default="{ row }">
                  {{ formatAlarmCycle(row) }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 0 ? 'success' : 'danger'">
                    {{ alarmStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="启用" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.enabled ? 'success' : 'info'">
                    {{ row.enabled ? "是" : "否" }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.updatedAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-popconfirm title="确认软删除这条闹钟吗？" @confirm="handleDeleteAlarm(row)">
                    <template #reference>
                      <el-button type="danger" size="small" plain>删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
            <div class="pager">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next, jumper"
                :total="totals.alarms"
                :current-page="paging.alarms.page"
                :page-size="paging.alarms.pageSize"
                :page-sizes="pageSizeOptions"
                @current-change="(page) => handleTabPageChange('alarms', page)"
                @size-change="(size) => handleTabSizeChange('alarms', size)"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="提醒日志" name="logs">
          <div class="table-card">
            <el-table :data="logs" stripe>
              <el-table-column prop="id" label="ID" width="90" />
              <el-table-column prop="alarmId" label="闹钟 ID" width="100" />
              <el-table-column prop="userId" label="用户 ID" width="100" />
              <el-table-column label="动作" width="120">
                <template #default="{ row }">
                  {{ alarmActionLabel(row.action) }}
                </template>
              </el-table-column>
              <el-table-column prop="deviceId" label="设备 ID" min-width="220" />
              <el-table-column prop="logHash" label="日志哈希" min-width="280" />
              <el-table-column label="触发时间" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.firedAt) }}
                </template>
              </el-table-column>
            </el-table>
            <div class="pager">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next, jumper"
                :total="totals.logs"
                :current-page="paging.logs.page"
                :page-size="paging.logs.pageSize"
                :page-sizes="pageSizeOptions"
                @current-change="(page) => handleTabPageChange('logs', page)"
                @size-change="(size) => handleTabSizeChange('logs', size)"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="审计日志" name="audits">
          <div class="table-card">
            <el-table :data="audits" stripe>
              <el-table-column prop="id" label="ID" width="90" />
              <el-table-column prop="adminUserId" label="管理员 ID" width="100" />
              <el-table-column prop="action" label="动作" width="180" />
              <el-table-column prop="targetType" label="目标类型" width="120" />
              <el-table-column prop="targetId" label="目标 ID" width="120" />
              <el-table-column prop="ipAddress" label="IP 地址" width="160" />
              <el-table-column label="记录时间" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt) }}
                </template>
              </el-table-column>
            </el-table>
            <div class="pager">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next, jumper"
                :total="totals.audits"
                :current-page="paging.audits.page"
                :page-size="paging.audits.pageSize"
                :page-sizes="pageSizeOptions"
                @current-change="(page) => handleTabPageChange('audits', page)"
                @size-change="(size) => handleTabSizeChange('audits', size)"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<style scoped>
.page-shell {
  min-height: 100vh;
  padding: 32px;
  background:
    radial-gradient(circle at top left, rgba(66, 153, 225, 0.18), transparent 32%),
    linear-gradient(180deg, #f6fbff 0%, #eef4fb 100%);
  color: #17324d;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #4a90c6;
}

h1 {
  margin: 0 0 8px;
  font-size: 32px;
}

.subtitle {
  margin: 0;
  max-width: 720px;
  color: #5d7995;
}

.login-card {
  max-width: 420px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.table-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

.tabs :deep(.el-card),
.tabs :deep(.el-table),
.tabs :deep(.el-pagination.is-background .btn-next),
.tabs :deep(.el-pagination.is-background .btn-prev),
.tabs :deep(.el-pagination.is-background .el-pager li) {
  border-radius: 16px;
}

@media (max-width: 900px) {
  .page-shell {
    padding: 20px;
  }

  .hero,
  .toolbar {
    flex-direction: column;
  }

  .pager {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
