export const BOARD_STATUS_MAP: Record<string, { label: string; color: string; tagType: string }> = {
  pending_review: { label: '待 Review', color: '#909399', tagType: 'info' },
  reviewing: { label: 'Review 中', color: '#e6a23c', tagType: 'warning' },
  ci_checking: { label: 'CI 检查中', color: '#409eff', tagType: 'primary' },
  conflict: { label: '冲突待解决', color: '#f56c6c', tagType: 'danger' },
  ready: { label: '可合并', color: '#67c23a', tagType: 'success' },
  merged: { label: '已合并', color: '#409eff', tagType: 'primary' },
  closed: { label: '已关闭', color: '#909399', tagType: 'info' },
}

export const BOARD_STATUS_KEYS = Object.keys(BOARD_STATUS_MAP)

export function getStatusLabel(status: string): string {
  return BOARD_STATUS_MAP[status]?.label || status
}

export function getStatusTagType(status: string): string {
  return BOARD_STATUS_MAP[status]?.tagType || 'info'
}

export function getStatusColor(status: string): string {
  return BOARD_STATUS_MAP[status]?.color || '#909399'
}
