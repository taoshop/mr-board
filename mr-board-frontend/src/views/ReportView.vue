<template>
  <div class="report-page">
    <!-- 顶部筛选栏 -->
    <el-card class="filter-bar" shadow="never">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="时间维度">
          <el-radio-group v-model="timeRange" @change="handleTimeRangeChange">
            <el-radio-button label="week">本周</el-radio-button>
            <el-radio-button label="month">本月</el-radio-button>
            <el-radio-button label="custom">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="timeRange === 'custom'" label="日期范围">
          <el-date-picker
            v-model="customDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            @change="loadAll"
          />
        </el-form-item>
        <el-form-item label="分组">
          <el-radio-group v-model="groupBy" @change="loadTrend">
            <el-radio-button label="day">按日</el-radio-button>
            <el-radio-button label="week">按周</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button :loading="exporting" @click="handleExportExcel">
            <el-icon><Download /></el-icon> 导出 Excel
          </el-button>
          <el-button :loading="exporting" @click="handleExportCsv">
            <el-icon><Download /></el-icon> 导出 CSV
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 概览 KPI -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="24" :sm="12" :md="8" :lg="4" v-for="kpi in kpis" :key="kpi.label">
        <el-card class="kpi-card" shadow="hover">
          <div class="kpi-value" :style="{ color: kpi.color }">{{ kpi.value }}</div>
          <div class="kpi-label">{{ kpi.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图 -->
    <el-card class="chart-card" shadow="never">
      <template #header>
        <span class="card-title">MR 趋势</span>
      </template>
      <div ref="trendChartRef" class="chart-container"></div>
      <el-empty v-if="trendEmpty" description="暂无趋势数据" />
    </el-card>

    <!-- 分布图 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span class="card-title">状态分布</span>
          </template>
          <div ref="statusChartRef" class="chart-container"></div>
          <el-empty v-if="statusEmpty" description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span class="card-title">项目分布</span>
          </template>
          <div ref="projectChartRef" class="chart-container"></div>
          <el-empty v-if="projectEmpty" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  getReportOverview,
  getReportTrend,
  getReportDistribution,
  submitAsyncExport,
  getExportStatus,
  downloadExportFile,
  type ReportOverview,
  type ReportTrend,
  type ReportDistribution,
  type ExportTask,
} from '@/api/report'

// 时间维度
const timeRange = ref('week')
const customDateRange = ref<string[]>([])
const groupBy = ref<'day' | 'week'>('day')

// 数据
const overview = ref<ReportOverview | null>(null)
const trend = ref<ReportTrend | null>(null)
const statusDist = ref<ReportDistribution | null>(null)
const projectDist = ref<ReportDistribution | null>(null)
const exporting = ref(false)

const exportTaskId = ref<string | null>(null)
let exportTimer: ReturnType<typeof setInterval> | null = null

const trendEmpty = computed(() => !trend.value || trend.value.labels.length === 0)
const statusEmpty = computed(() => !statusDist || statusDist.value?.labels.length === 0)
const projectEmpty = computed(() => !projectDist || projectDist.value?.labels.length === 0)

// KPI 卡片数据
const kpis = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { label: '总 MR 数', value: o.totalMrCount, color: '#409EFF' },
    { label: '已合并', value: o.mergedMrCount, color: '#67C23A' },
    { label: '开放中', value: o.openMrCount, color: '#E6A23C' },
    { label: '平均合并时长(h)', value: o.avgMergeHours ?? 0, color: '#909399' },
    { label: 'CI 成功率(%)', value: (o.ciSuccessRate ?? 0).toFixed(2), color: '#67C23A' },
    { label: '冲突率(%)', value: (o.conflictRate ?? 0).toFixed(2), color: '#F56C6C' },
  ]
})

// ECharts 实例
let trendChart: echarts.ECharts | null = null
let statusChart: echarts.ECharts | null = null
let projectChart: echarts.ECharts | null = null
const trendChartRef = ref<HTMLDivElement>()
const statusChartRef = ref<HTMLDivElement>()
const projectChartRef = ref<HTMLDivElement>()

function getWeekString(d: Date) {
  const year = d.getFullYear()
  const start = new Date(year, 0, 1)
  const diff = d.getTime() - start.getTime()
  const oneDay = 86400000
  const dayOfYear = Math.floor(diff / oneDay)
  const week = Math.ceil(dayOfYear / 7)
  return `${year}-W${String(week).padStart(2, '0')}`
}

function getMonthString(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

function getDateRange(): { start: string; end: string; week?: string; month?: string } {
  const now = new Date()
  if (timeRange.value === 'week') {
    const day = now.getDay() || 7
    const start = new Date(now)
    start.setDate(now.getDate() - day + 1)
    const end = new Date(start)
    end.setDate(start.getDate() + 6)
    return {
      start: start.toISOString().split('T')[0],
      end: end.toISOString().split('T')[0],
      week: getWeekString(now),
    }
  }
  if (timeRange.value === 'month') {
    const start = new Date(now.getFullYear(), now.getMonth(), 1)
    const end = new Date(now.getFullYear(), now.getMonth() + 1, 0)
    return {
      start: start.toISOString().split('T')[0],
      end: end.toISOString().split('T')[0],
      month: getMonthString(now),
    }
  }
  if (customDateRange.value && customDateRange.value.length === 2) {
    return {
      start: customDateRange.value[0],
      end: customDateRange.value[1],
    }
  }
  // fallback
  return {
    start: now.toISOString().split('T')[0],
    end: now.toISOString().split('T')[0],
  }
}

async function loadOverview() {
  try {
    const range = getDateRange()
    const params: any = {}
    if (range.week) params.week = range.week
    else if (range.month) params.month = range.month
    const res = await getReportOverview(params)
    overview.value = res.data
  } catch {
    overview.value = null
  }
}

async function loadTrend() {
  try {
    const range = getDateRange()
    const res = await getReportTrend({
      start: range.start,
      end: range.end,
      groupBy: groupBy.value,
    })
    trend.value = res.data
    await nextTick()
    renderTrendChart()
  } catch {
    trend.value = null
  }
}

async function loadDistribution() {
  try {
    const [statusRes, projectRes] = await Promise.all([
      getReportDistribution('status'),
      getReportDistribution('project'),
    ])
    statusDist.value = statusRes.data
    projectDist.value = projectRes.data
    await nextTick()
    renderStatusChart()
    renderProjectChart()
  } catch {
    statusDist.value = null
    projectDist.value = null
  }
}

function loadAll() {
  loadOverview()
  loadTrend()
  loadDistribution()
}

function handleTimeRangeChange() {
  loadAll()
}

// ECharts 渲染
function renderTrendChart() {
  if (!trendChartRef.value || !trend.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const data = trend.value
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['创建', '合并', '关闭'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: data.labels },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '创建', type: 'line', data: data.createdData, smooth: true, itemStyle: { color: '#409EFF' } },
      { name: '合并', type: 'line', data: data.mergedData, smooth: true, itemStyle: { color: '#67C23A' } },
      { name: '关闭', type: 'line', data: data.closedData, smooth: true, itemStyle: { color: '#909399' } },
    ],
  })
}

function renderStatusChart() {
  if (!statusChartRef.value || !statusDist.value) return
  if (!statusChart) statusChart = echarts.init(statusChartRef.value)
  const data = statusDist.value
  statusChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: data.labels },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: data.values,
        itemStyle: { color: '#409EFF', borderRadius: [4, 4, 0, 0] },
      },
    ],
  })
}

function renderProjectChart() {
  if (!projectChartRef.value || !projectDist.value) return
  if (!projectChart) projectChart = echarts.init(projectChartRef.value)
  const data = projectDist.value
  projectChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left', type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
        data: data.labels.map((label, i) => ({ value: data.values[i], name: label })),
      },
    ],
  })
}

// 异步导出
function startExportPolling(taskId: string, filename: string) {
  exportTaskId.value = taskId
  exporting.value = true
  ElMessage.info('导出任务已提交，请稍候...')

  exportTimer = setInterval(async () => {
    try {
      const res = await getExportStatus(taskId)
      const task: ExportTask = res.data
      if (task.status === 'COMPLETED') {
        stopExportPolling()
        const blob = await downloadExportFile(taskId)
        downloadBlob(blob, filename)
        ElMessage.success('导出成功')
      } else if (task.status === 'FAILED') {
        stopExportPolling()
        ElMessage.error(task.errorMsg || '导出失败')
      }
      // PENDING / RUNNING 继续轮询
    } catch {
      stopExportPolling()
      ElMessage.error('查询导出状态失败')
    }
  }, 2000)
}

function stopExportPolling() {
  exporting.value = false
  exportTaskId.value = null
  if (exportTimer) {
    clearInterval(exportTimer)
    exportTimer = null
  }
}

async function handleExportExcel() {
  try {
    const res = await submitAsyncExport('excel')
    startExportPolling(res.data.id, `MR列表_${new Date().toISOString().split('T')[0]}.xlsx`)
  } catch {
    ElMessage.error('提交导出任务失败')
    exporting.value = false
  }
}

async function handleExportCsv() {
  try {
    const res = await submitAsyncExport('csv')
    startExportPolling(res.data.id, `MR列表_${new Date().toISOString().split('T')[0]}.csv`)
  } catch {
    ElMessage.error('提交导出任务失败')
    exporting.value = false
  }
}

function downloadBlob(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}

// 窗口大小变化时重绘
function onResize() {
  trendChart?.resize()
  statusChart?.resize()
  projectChart?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose()
  statusChart?.dispose()
  projectChart?.dispose()
  stopExportPolling()
})

watch(timeRange, () => {
  if (timeRange.value !== 'custom') {
    customDateRange.value = []
  }
})
</script>

<style scoped lang="scss">
.report-page {
  .filter-bar {
    margin-bottom: 16px;
    .filter-form {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
    }
  }

  .kpi-row {
    margin-bottom: 16px;
    .kpi-card {
      text-align: center;
      .kpi-value {
        font-size: 28px;
        font-weight: 700;
        line-height: 1.2;
        margin-bottom: 4px;
      }
      .kpi-label {
        font-size: 13px;
        color: #606266;
      }
    }
  }

  .chart-card {
    margin-bottom: 16px;
    .card-title {
      font-weight: 600;
      font-size: 15px;
    }
    .chart-container {
      width: 100%;
      height: 320px;
    }
  }

  .chart-row {
    margin-bottom: 16px;
  }
}
</style>
