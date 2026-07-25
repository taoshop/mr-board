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
  return request.get('/board/columns') as Promise<any>
}

export function getBoard(params?: {
  projectId?: number
  status?: string
  author?: string
  branch?: string
}) {
  return request.get('/board', { params }) as Promise<any>
}

export function getProjects(gitSourceId?: number) {
  return request.get('/projects', { params: { gitSourceId } }) as Promise<any>
}

export function getMrCi(id: number) {
  return request.get(`/board/mr/${id}/ci`) as Promise<any>
}

export function getMrList(params?: MrPageParams) {
  return request.get('/mrs', { params }) as Promise<any>
}

export function getMrDetail(id: number) {
  return request.get(`/mrs/${id}`) as Promise<any>
}

export function getMrChanges(id: number) {
  return request.get(`/mrs/${id}/changes`) as Promise<any>
}

export function updateMrStatus(id: number, boardStatus: string) {
  return request.put(`/mrs/${id}/status`, { boardStatus }) as Promise<any>
}

export interface CommentItem {
  id: number
  mrId: number
  platformCommentId: string
  authorName: string
  authorAvatar?: string
  body: string
  isSystem?: number
  createdAt: string
}

export function getMrComments(id: number) {
  return request.get(`/mrs/${id}/comments`) as Promise<any>
}
