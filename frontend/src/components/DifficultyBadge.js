import React from 'react';
export default function DifficultyBadge({ difficulty }) {
  const cls = {
    EASY:   'badge-easy',
    MEDIUM: 'badge-medium',
    HARD:   'badge-hard',
  }[difficulty] || 'badge-easy';
  const labels = { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' };
  return <span className={cls}>{labels[difficulty] || difficulty}</span>;
}
