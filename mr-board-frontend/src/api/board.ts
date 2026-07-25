import request from '@/utils/request'

export interface Column {
  key: string
  label: string
  color: string
}

export interface Mr {
  id: number
  platformMrId: number
  title: string
  authorName: string
  authorAvatar?: string
  sourceBranch: string
  targetBranch: string
  boardStatus: string
  ciStatus?: string
  commentsCount?: number
  changesCount?: number
  platformStatus?: string
  webUrl?: string
}

export interface MrPageParams {
  page?: number
  size?: number
  projectId?: number
  boardStatus?: string
  author?: string
  branch?: string
}

export interface MrDetail {
  mr: Mr
  ciJobs: CiJob[]
  statusHistory: StatusHistory[]
}

export interface StatusHistory {
  id: number
  mrId: number
  fromStatus?: string
  toStatus: string
  operatorName?: string
  createdAt: string
}

export interface ChangeItem {
  oldPath: string
  newPath: string
  status: string
  additions?: number
  deletions?: number
  diff?: string
}

export interface CiJob {
  id: number
  projectId: number
  platformMrId: number
  platformJobId: string
  name: string
  stage: string
  status: string
  logUrl?: string
  startedAt?: string
  finishedAt?: string
}

export function getColumns() {
  return request.get('/api/board/columns')
}

export function getBoard(params?: {
  projectId?: number
  status?: string
  author?: string
  branch?: string
}) {
  return request.get('/api/board', { params })
}

export function getProjects(gitSourceId?: number) {
  return request.get('/api/projects', { params: { gitSourceId } })
}

export function getMrCi(id: number) {
  return request.get(`/api/board/mr/${id}/ci`)
}

export function getMrList(params?: MrPageParams) {
  return request.get('/api/mrs', { params })
}

export function getMrDetail(id: number) {
  return request.get(`/api/mrs/${id}`)
}

export function getMrChanges(id: number) {
  return request.get(`/api/mrs/${id}/changes`)
}

export function updateMrStatus(id: number, boardStatus: string) {
  return request.put(`/api/mrs/${id}/status`, { boardStatus })
}
