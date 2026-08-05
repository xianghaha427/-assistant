// 会议 API 已合并到 api/index.js，此文件保留为兼容层
import {
  createMeeting as _createMeeting,
  getMeetings as _getMeetings,
  getMeeting as _getMeeting,
  deleteMeeting as _deleteMeeting,
  generateMeetingSummary as _generateMeetingSummary,
  getMeetingSummary as _getMeetingSummary,
  getMeetingActions as _getMeetingActions,
  updateActionStatus as _updateActionStatus,
  uploadMeetingAudio as _uploadMeetingAudio
} from './index.js'

// 新名称
export {
  _createMeeting as createMeeting,
  _getMeetings as getMeetings,
  _getMeeting as getMeeting,
  _deleteMeeting as deleteMeeting,
  _generateMeetingSummary as generateMeetingSummary,
  _getMeetingSummary as getMeetingSummary,
  _getMeetingActions as getMeetingActions,
  _updateActionStatus as updateActionStatus,
  _uploadMeetingAudio as uploadMeetingAudio
}

// 旧名称（兼容页面组件）
export {
  _generateMeetingSummary as generateSummary,
  _getMeetingSummary as getSummary,
  _getMeetingActions as getActions,
  _updateActionStatus as updateAction,
  _uploadMeetingAudio as uploadAudio
}
