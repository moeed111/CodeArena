import React from 'react';
const STATUS_MAP = {
  ACCEPTED:              { label: 'Accepted',           cls: 'text-green-400  bg-green-900/30  border-green-700/50' },
  WRONG_ANSWER:          { label: 'Wrong Answer',       cls: 'text-red-400    bg-red-900/30    border-red-700/50'   },
  TIME_LIMIT_EXCEEDED:   { label: 'Time Limit',         cls: 'text-yellow-400 bg-yellow-900/30 border-yellow-700/50'},
  MEMORY_LIMIT_EXCEEDED: { label: 'Memory Limit',       cls: 'text-orange-400 bg-orange-900/30 border-orange-700/50'},
  COMPILE_ERROR:         { label: 'Compile Error',      cls: 'text-orange-400 bg-orange-900/30 border-orange-700/50'},
  RUNTIME_ERROR:         { label: 'Runtime Error',      cls: 'text-orange-400 bg-orange-900/30 border-orange-700/50'},
  PENDING:               { label: 'Pending',            cls: 'text-gray-400   bg-gray-900/30   border-gray-700/50'  },
  RUNNING:               { label: 'Running…',           cls: 'text-blue-400   bg-blue-900/30   border-blue-700/50'  },
};
export default function StatusBadge({ status }) {
  const { label, cls } = STATUS_MAP[status] || { label: status, cls: 'text-gray-400 bg-gray-900/30 border-gray-700/50' };
  return (
    <span className={`px-2 py-0.5 rounded text-xs font-semibold border ${cls}`}>{label}</span>
  );
}
