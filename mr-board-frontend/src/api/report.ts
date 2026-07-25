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
  return request.get('/api/reports/overview', { params }) as Promise<{ data: ReportOverview }>
}

export function getReportTrend(params: {
  start: string
  end: string
  groupBy?: 'day' | 'week'
}) {
  return request.get('/api/reports/trend', { params }) as Promise<{ data: ReportTrend }>
}

export function getReportDistribution(type: 'project' | 'author' | 'status') {
  return request.get('/api/reports/distribution', { params: { type } }) as Promise<{
    data: ReportDistribution
  }>
}

export function exportExcel() {
  return request.get('/api/reports/export/excel', {
    responseType: 'blob',
  }) as Promise<Blob>
}

export function exportCsv() {
  return request.get('/api/reports/export/csv', {
    responseType: 'blob',
  }) as Promise<Blob>
}
