import request from '@/utils/request'

export interface ReportOverview {
  totalMrCount: number
  mergedMrCount: number
  openMrCount: number
  avgMergeHours: number
  ciSuccessRate: number
  conflictRate: number
}

export interface ReportTrend {
  labels: string[]
  createdData: number[]
  mergedData: number[]
  closedData: number[]
}

export interface ReportDistribution {
  labels: string[]
  values: number[]
}

export function getReportOverview(params: { week?: string; month?: string }) {
  return request.get('/reports/overview', { params }) as Promise<{ data: ReportOverview }>
}

export function getReportTrend(params: {
  start: string
  end: string
  groupBy?: 'day' | 'week'
}) {
  return request.get('/reports/trend', { params }) as Promise<{ data: ReportTrend }>
}

export function getReportDistribution(type: 'project' | 'author' | 'status') {
  return request.get('/reports/distribution', { params: { type } }) as Promise<{
    data: ReportDistribution
  }>
}

export function exportExcel() {
  return request.get('/reports/export/excel', {
    responseType: 'blob',
  }) as Promise<Blob>
}

export function exportCsv() {
  return request.get('/reports/export/csv', {
    responseType: 'blob',
  }) as Promise<Blob>
}

export interface ExportTask {
  id: string
  type: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  filePath?: string
  errorMsg?: string
  createdAt: string
  completedAt?: string
}

export function submitAsyncExport(type: 'excel' | 'csv') {
  return request.post('/reports/export/async', null, { params: { type } }) as Promise<{ data: ExportTask }>
}

export function getExportStatus(taskId: string) {
  return request.get(`/reports/export/status/${taskId}`) as Promise<{ data: ExportTask }>
}

export function downloadExportFile(taskId: string) {
  return request.get(`/reports/export/download/${taskId}`, {
    responseType: 'blob',
  }) as Promise<Blob>
}
