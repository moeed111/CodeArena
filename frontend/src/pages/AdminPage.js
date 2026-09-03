import React, { useState, useEffect, useCallback } from 'react';
import { adminApi, problemsApi } from '../api/index';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const DIFFICULTY_COLORS = {
  EASY:   { bg: '#00b8a320', text: '#00b8a3', border: '#00b8a340' },
  MEDIUM: { bg: '#ffc01e20', text: '#ffc01e', border: '#ffc01e40' },
  HARD:   { bg: '#ff375f20', text: '#ff375f', border: '#ff375f40' },
};

const ROLE_COLORS = {
  ADMIN: { bg: '#a78bfa20', text: '#a78bfa', border: '#a78bfa40' },
  USER:  { bg: '#60a5fa20', text: '#60a5fa', border: '#60a5fa40' },
};

function Badge({ label, colors }) {
  return (
    <span style={{
      background: colors.bg,
      color: colors.text,
      border: `1px solid ${colors.border}`,
      padding: '2px 10px',
      borderRadius: 6,
      fontSize: 11,
      fontWeight: 700,
      letterSpacing: '0.05em',
      textTransform: 'uppercase',
    }}>{label}</span>
  );
}

function Spinner() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
      <div style={{
        width: 36, height: 36, border: '3px solid #1e1e3f',
        borderTop: '3px solid #a78bfa', borderRadius: '50%',
        animation: 'spin 0.8s linear infinite',
      }} />
    </div>
  );
}

function StatCard({ icon, label, value, color }) {
  return (
    <div style={{
      background: 'linear-gradient(135deg, #1a1a35 0%, #12122a 100%)',
      border: `1px solid ${color}30`,
      borderRadius: 16,
      padding: '24px 28px',
      display: 'flex',
      alignItems: 'center',
      gap: 18,
      boxShadow: `0 4px 24px ${color}15`,
      flex: 1,
      minWidth: 180,
    }}>
      <div style={{
        width: 52, height: 52, borderRadius: 14,
        background: `${color}20`,
        border: `1px solid ${color}40`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontSize: 24, flexShrink: 0,
      }}>{icon}</div>
      <div>
        <div style={{ color: '#8b8ba7', fontSize: 12, fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase', marginBottom: 4 }}>{label}</div>
        <div style={{ color: '#e2e2f0', fontSize: 28, fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>{value?.toLocaleString() ?? '—'}</div>
      </div>
    </div>
  );
}

// ─── Problem Form Modal ───────────────────────────────────────────────────────

const EMPTY_FORM = {
  title: '', description: '', difficulty: 'EASY', constraints: '',
  starterCode: '', solution: '', tags: '',
  examples: [{ input: '', output: '', explanation: '' }],
  testCases: [{ input: '', expected: '', hidden: false }],
};

function ProblemModal({ initial, onClose, onSave }) {
  const [form, setForm] = useState(() => {
    if (!initial) return EMPTY_FORM;
    return {
      title:       initial.title || '',
      description: initial.description || '',
      difficulty:  initial.difficulty || 'EASY',
      constraints: initial.constraints || '',
      starterCode: initial.starterCode || '',
      solution:    initial.solution || '',
      tags:        (initial.tags || []).join(', '),
      examples:    initial.examples?.length
        ? initial.examples.map(e => ({ input: e.input, output: e.output, explanation: e.explanation || '' }))
        : [{ input: '', output: '', explanation: '' }],
      testCases:   initial.testCases?.length
        ? initial.testCases.map(t => ({ input: t.input, expected: t.expected, hidden: t.hidden || false }))
        : [{ input: '', expected: '', hidden: false }],
    };
  });
  const [saving, setSaving] = useState(false);
  const [error,  setError]  = useState('');

  const set = (key, val) => setForm(f => ({ ...f, [key]: val }));

  const setEx = (i, key, val) => setForm(f => {
    const ex = [...f.examples]; ex[i] = { ...ex[i], [key]: val }; return { ...f, examples: ex };
  });
  const addEx  = () => setForm(f => ({ ...f, examples: [...f.examples, { input: '', output: '', explanation: '' }] }));
  const removeEx = i => setForm(f => ({ ...f, examples: f.examples.filter((_, j) => j !== i) }));

  const setTc = (i, key, val) => setForm(f => {
    const tc = [...f.testCases]; tc[i] = { ...tc[i], [key]: val }; return { ...f, testCases: tc };
  });
  const addTc  = () => setForm(f => ({ ...f, testCases: [...f.testCases, { input: '', expected: '', hidden: false }] }));
  const removeTc = i => setForm(f => ({ ...f, testCases: f.testCases.filter((_, j) => j !== i) }));

  const handleSubmit = async () => {
    if (!form.title.trim() || !form.description.trim()) {
      setError('Title and Description are required.'); return;
    }
    setSaving(true); setError('');
    try {
      const payload = {
        title:       form.title,
        description: form.description,
        difficulty:  form.difficulty,
        constraints: form.constraints || null,
        starterCode: form.starterCode || null,
        solution:    form.solution    || null,
        tags:        form.tags ? form.tags.split(',').map(t => t.trim()).filter(Boolean) : [],
        examples:    form.examples.map((e, i) => ({ ...e, orderIndex: i })),
        testCases:   form.testCases.map((t, i) => ({ ...t, orderIndex: i })),
      };
      await onSave(payload);
      onClose();
    } catch (e) {
      setError(e.response?.data?.message || 'Failed to save problem.');
    } finally {
      setSaving(false);
    }
  };

  const overlay = {
    position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)',
    backdropFilter: 'blur(4px)', zIndex: 1000,
    display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
  };
  const modal = {
    background: '#12122a', border: '1px solid #2a2a50',
    borderRadius: 20, width: '100%', maxWidth: 780,
    maxHeight: '90vh', overflowY: 'auto', padding: '32px 36px',
    boxShadow: '0 24px 80px rgba(0,0,0,0.6)',
  };

  const inp = {
    width: '100%', background: '#0d0d1f', border: '1px solid #2a2a50',
    borderRadius: 10, padding: '10px 14px', color: '#e2e2f0',
    fontSize: 14, outline: 'none', boxSizing: 'border-box',
    fontFamily: 'inherit', transition: 'border-color 0.2s',
  };
  const label = { color: '#8b8ba7', fontSize: 12, fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase', marginBottom: 6, display: 'block' };
  const field = { marginBottom: 18 };

  return (
    <div style={overlay} onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div style={modal}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 28 }}>
          <h2 style={{ color: '#e2e2f0', fontSize: 22, fontWeight: 700, margin: 0 }}>
            {initial ? '✏️ Edit Problem' : '➕ Add Problem'}
          </h2>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#8b8ba7', fontSize: 22, cursor: 'pointer' }}>✕</button>
        </div>

        {error && <div style={{ background: '#ff375f15', border: '1px solid #ff375f40', borderRadius: 10, padding: '10px 14px', color: '#ff375f', marginBottom: 18, fontSize: 14 }}>{error}</div>}

        <div style={field}>
          <label style={label}>Title *</label>
          <input style={inp} value={form.title} onChange={e => set('title', e.target.value)} placeholder="Two Sum" />
        </div>

        <div style={field}>
          <label style={label}>Description *</label>
          <textarea style={{ ...inp, minHeight: 120, resize: 'vertical' }} value={form.description} onChange={e => set('description', e.target.value)} placeholder="Problem statement..." />
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 18 }}>
          <div>
            <label style={label}>Difficulty</label>
            <select style={{ ...inp }} value={form.difficulty} onChange={e => set('difficulty', e.target.value)}>
              <option>EASY</option><option>MEDIUM</option><option>HARD</option>
            </select>
          </div>
          <div>
            <label style={label}>Tags (comma-separated)</label>
            <input style={inp} value={form.tags} onChange={e => set('tags', e.target.value)} placeholder="Array, Hash Table" />
          </div>
        </div>

        <div style={field}>
          <label style={label}>Constraints</label>
          <textarea style={{ ...inp, minHeight: 70, resize: 'vertical' }} value={form.constraints} onChange={e => set('constraints', e.target.value)} placeholder="1 ≤ n ≤ 10^4" />
        </div>

        <div style={field}>
          <label style={label}>Starter Code</label>
          <textarea style={{ ...inp, minHeight: 90, resize: 'vertical', fontFamily: 'monospace', fontSize: 13 }} value={form.starterCode} onChange={e => set('starterCode', e.target.value)} placeholder="function twoSum(nums, target) {}" />
        </div>

        <div style={field}>
          <label style={label}>Solution</label>
          <textarea style={{ ...inp, minHeight: 90, resize: 'vertical', fontFamily: 'monospace', fontSize: 13 }} value={form.solution} onChange={e => set('solution', e.target.value)} placeholder="Full solution code..." />
        </div>

        {/* Examples */}
        <div style={{ marginBottom: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <span style={{ ...label, margin: 0 }}>Examples</span>
            <button onClick={addEx} style={{ background: '#a78bfa20', border: '1px solid #a78bfa40', color: '#a78bfa', borderRadius: 8, padding: '4px 12px', cursor: 'pointer', fontSize: 12 }}>+ Add</button>
          </div>
          {form.examples.map((ex, i) => (
            <div key={i} style={{ background: '#0d0d1f', border: '1px solid #1e1e3f', borderRadius: 10, padding: 14, marginBottom: 10 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                <span style={{ color: '#8b8ba7', fontSize: 12 }}>Example {i + 1}</span>
                {form.examples.length > 1 && <button onClick={() => removeEx(i)} style={{ background: 'none', border: 'none', color: '#ff375f', cursor: 'pointer', fontSize: 12 }}>Remove</button>}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 10 }}>
                <div><label style={{ ...label, fontSize: 11 }}>Input</label><input style={inp} value={ex.input} onChange={e => setEx(i, 'input', e.target.value)} /></div>
                <div><label style={{ ...label, fontSize: 11 }}>Output</label><input style={inp} value={ex.output} onChange={e => setEx(i, 'output', e.target.value)} /></div>
              </div>
              <div><label style={{ ...label, fontSize: 11 }}>Explanation</label><input style={inp} value={ex.explanation} onChange={e => setEx(i, 'explanation', e.target.value)} /></div>
            </div>
          ))}
        </div>

        {/* Test Cases */}
        <div style={{ marginBottom: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <span style={{ ...label, margin: 0 }}>Test Cases</span>
            <button onClick={addTc} style={{ background: '#a78bfa20', border: '1px solid #a78bfa40', color: '#a78bfa', borderRadius: 8, padding: '4px 12px', cursor: 'pointer', fontSize: 12 }}>+ Add</button>
          </div>
          {form.testCases.map((tc, i) => (
            <div key={i} style={{ background: '#0d0d1f', border: '1px solid #1e1e3f', borderRadius: 10, padding: 14, marginBottom: 10 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#8b8ba7', fontSize: 12, cursor: 'pointer' }}>
                  <input type="checkbox" checked={tc.hidden} onChange={e => setTc(i, 'hidden', e.target.checked)} />
                  Hidden (test case {i + 1})
                </label>
                {form.testCases.length > 1 && <button onClick={() => removeTc(i)} style={{ background: 'none', border: 'none', color: '#ff375f', cursor: 'pointer', fontSize: 12 }}>Remove</button>}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <div><label style={{ ...label, fontSize: 11 }}>Input</label><input style={inp} value={tc.input} onChange={e => setTc(i, 'input', e.target.value)} /></div>
                <div><label style={{ ...label, fontSize: 11 }}>Expected Output</label><input style={inp} value={tc.expected} onChange={e => setTc(i, 'expected', e.target.value)} /></div>
              </div>
            </div>
          ))}
        </div>

        <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
          <button onClick={onClose} style={{ background: 'none', border: '1px solid #2a2a50', color: '#8b8ba7', borderRadius: 10, padding: '10px 24px', cursor: 'pointer', fontSize: 14 }}>Cancel</button>
          <button onClick={handleSubmit} disabled={saving} style={{
            background: saving ? '#3730a3' : 'linear-gradient(135deg, #7c3aed, #a78bfa)',
            border: 'none', color: '#fff', borderRadius: 10, padding: '10px 28px',
            cursor: saving ? 'not-allowed' : 'pointer', fontSize: 14, fontWeight: 600,
          }}>{saving ? 'Saving…' : (initial ? 'Update Problem' : 'Create Problem')}</button>
        </div>
      </div>
    </div>
  );
}

// ─── Confirm Dialog ───────────────────────────────────────────────────────────

function ConfirmDialog({ message, onConfirm, onCancel }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)', zIndex: 1100, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ background: '#12122a', border: '1px solid #2a2a50', borderRadius: 16, padding: '32px 36px', maxWidth: 420, width: '100%', textAlign: 'center' }}>
        <div style={{ fontSize: 40, marginBottom: 12 }}>⚠️</div>
        <p style={{ color: '#e2e2f0', marginBottom: 24, fontSize: 15, lineHeight: 1.6 }}>{message}</p>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
          <button onClick={onCancel} style={{ background: 'none', border: '1px solid #2a2a50', color: '#8b8ba7', borderRadius: 10, padding: '9px 22px', cursor: 'pointer', fontSize: 14 }}>Cancel</button>
          <button onClick={onConfirm} style={{ background: 'linear-gradient(135deg, #dc2626, #ef4444)', border: 'none', color: '#fff', borderRadius: 10, padding: '9px 22px', cursor: 'pointer', fontSize: 14, fontWeight: 600 }}>Delete</button>
        </div>
      </div>
    </div>
  );
}

// ─── Tab: Dashboard ───────────────────────────────────────────────────────────

function DashboardTab({ stats, problemStats }) {
  return (
    <div>
      <h2 style={{ color: '#e2e2f0', fontSize: 20, fontWeight: 700, marginBottom: 24 }}>📊 Overview</h2>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 32 }}>
        <StatCard icon="👥" label="Total Users"       value={stats?.totalUsers}       color="#a78bfa" />
        <StatCard icon="📝" label="Total Problems"    value={stats?.totalProblems}    color="#60a5fa" />
        <StatCard icon="🚀" label="Total Submissions" value={stats?.totalSubmissions} color="#34d399" />
      </div>
      {problemStats && (
        <>
          <h3 style={{ color: '#8b8ba7', fontSize: 14, fontWeight: 600, letterSpacing: '0.05em', textTransform: 'uppercase', marginBottom: 16 }}>Problems by Difficulty</h3>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            {['EASY', 'MEDIUM', 'HARD'].map(d => (
              <div key={d} style={{
                background: `${DIFFICULTY_COLORS[d].bg}`, border: `1px solid ${DIFFICULTY_COLORS[d].border}`,
                borderRadius: 14, padding: '20px 28px', flex: 1, minWidth: 140,
              }}>
                <div style={{ color: DIFFICULTY_COLORS[d].text, fontSize: 12, fontWeight: 700, letterSpacing: '0.05em', textTransform: 'uppercase', marginBottom: 6 }}>{d}</div>
                <div style={{ color: '#e2e2f0', fontSize: 32, fontWeight: 800 }}>{problemStats[d] ?? 0}</div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

// ─── Tab: Problems ────────────────────────────────────────────────────────────

function ProblemsTab() {
  const [problems,    setProblems]    = useState([]);
  const [loading,     setLoading]     = useState(true);
  const [modal,       setModal]       = useState(null);   // null | 'add' | problem object
  const [confirm,     setConfirm]     = useState(null);   // null | { id, type }
  const [search,      setSearch]      = useState('');
  const [actionMsg,   setActionMsg]   = useState('');

  const flash = msg => { setActionMsg(msg); setTimeout(() => setActionMsg(''), 3000); };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await problemsApi.getAll({ size: 200 });
      setProblems(data.content || []);
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const filtered = problems.filter(p =>
    p.title.toLowerCase().includes(search.toLowerCase())
  );

  const handleSave = async (payload) => {
    if (modal === 'add') {
      await adminApi.createProblem(payload);
      flash('✅ Problem created successfully!');
    } else {
      await adminApi.updateProblem(modal.id, payload);
      flash('✅ Problem updated successfully!');
    }
    await load();
  };

  const handleDelete = async () => {
    await adminApi.deleteProblem(confirm.id);
    setConfirm(null);
    flash('🗑️ Problem deleted.');
    await load();
  };

  const handleToggle = async (id) => {
    await adminApi.toggleProblem(id);
    await load();
  };

  const btnSm = (color, label, onClick) => (
    <button onClick={onClick} style={{
      background: `${color}18`, border: `1px solid ${color}40`, color,
      borderRadius: 7, padding: '4px 12px', cursor: 'pointer', fontSize: 12, fontWeight: 600,
      transition: 'all 0.15s',
    }}>{label}</button>
  );

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
        <h2 style={{ color: '#e2e2f0', fontSize: 20, fontWeight: 700, margin: 0 }}>📝 Problems</h2>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <input
            value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Search problems…"
            style={{ background: '#0d0d1f', border: '1px solid #2a2a50', borderRadius: 10, padding: '8px 14px', color: '#e2e2f0', fontSize: 13, outline: 'none', width: 220 }}
          />
          <button onClick={() => setModal('add')} style={{
            background: 'linear-gradient(135deg, #7c3aed, #a78bfa)',
            border: 'none', color: '#fff', borderRadius: 10, padding: '9px 18px',
            cursor: 'pointer', fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap',
          }}>+ Add Problem</button>
        </div>
      </div>

      {actionMsg && <div style={{ background: '#34d39915', border: '1px solid #34d39940', borderRadius: 10, padding: '10px 14px', color: '#34d399', marginBottom: 16, fontSize: 14 }}>{actionMsg}</div>}

      {loading ? <Spinner /> : (
        <div style={{ background: '#12122a', border: '1px solid #1e1e3f', borderRadius: 16, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
              <thead>
                <tr style={{ background: '#0d0d1f' }}>
                  {['#', 'Title', 'Difficulty', 'Tags', 'Acceptance', 'Status', 'Actions'].map(h => (
                    <th key={h} style={{ padding: '12px 16px', textAlign: 'left', color: '#8b8ba7', fontWeight: 600, fontSize: 11, letterSpacing: '0.05em', textTransform: 'uppercase', borderBottom: '1px solid #1e1e3f', whiteSpace: 'nowrap' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((p, idx) => (
                  <tr key={p.id} style={{ borderBottom: '1px solid #1a1a35', transition: 'background 0.15s' }}
                    onMouseEnter={e => e.currentTarget.style.background = '#1a1a35'}
                    onMouseLeave={e => e.currentTarget.style.background = 'transparent'}>
                    <td style={{ padding: '12px 16px', color: '#8b8ba7' }}>{idx + 1}</td>
                    <td style={{ padding: '12px 16px', color: '#e2e2f0', fontWeight: 500, maxWidth: 240 }}>
                      <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{p.title}</div>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <Badge label={p.difficulty} colors={DIFFICULTY_COLORS[p.difficulty] || DIFFICULTY_COLORS.EASY} />
                    </td>
                    <td style={{ padding: '12px 16px', color: '#8b8ba7', maxWidth: 180 }}>
                      <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {Array.from(p.tags || []).slice(0, 3).join(', ') || '—'}
                      </div>
                    </td>
                    <td style={{ padding: '12px 16px', color: '#60a5fa', fontVariantNumeric: 'tabular-nums' }}>
                      {p.acceptance ? `${Number(p.acceptance).toFixed(1)}%` : '0.0%'}
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ color: p.active !== false ? '#34d399' : '#ff375f', fontSize: 12, fontWeight: 600 }}>
                        {p.active !== false ? '● Active' : '○ Inactive'}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <div style={{ display: 'flex', gap: 6 }}>
                        {btnSm('#a78bfa', 'Edit',   () => setModal(p))}
                        {btnSm('#ffc01e', p.active !== false ? 'Deactivate' : 'Activate', () => handleToggle(p.id))}
                        {btnSm('#ff375f', 'Delete', () => setConfirm({ id: p.id, title: p.title }))}
                      </div>
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 && (
                  <tr><td colSpan={7} style={{ padding: 40, textAlign: 'center', color: '#8b8ba7' }}>No problems found.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {modal && (
        <ProblemModal
          initial={modal === 'add' ? null : modal}
          onClose={() => setModal(null)}
          onSave={handleSave}
        />
      )}

      {confirm && (
        <ConfirmDialog
          message={`Are you sure you want to delete "${confirm.title}"? This cannot be undone.`}
          onConfirm={handleDelete}
          onCancel={() => setConfirm(null)}
        />
      )}
    </div>
  );
}

// ─── Tab: Users ───────────────────────────────────────────────────────────────

function UsersTab({ currentUserId }) {
  const [users,     setUsers]     = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [confirm,   setConfirm]   = useState(null);
  const [search,    setSearch]    = useState('');
  const [actionMsg, setActionMsg] = useState('');

  const flash = msg => { setActionMsg(msg); setTimeout(() => setActionMsg(''), 3000); };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await adminApi.getUsers();
      setUsers(data);
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  const filtered = users.filter(u =>
    u.username.toLowerCase().includes(search.toLowerCase()) ||
    u.email.toLowerCase().includes(search.toLowerCase())
  );

  const handleRoleToggle = async (user) => {
    const newRole = user.role === 'ADMIN' ? 'USER' : 'ADMIN';
    await adminApi.updateUserRole(user.id, newRole);
    flash(`✅ ${user.username} is now ${newRole}`);
    await load();
  };

  const handleDelete = async () => {
    await adminApi.deleteUser(confirm.id);
    setConfirm(null);
    flash('🗑️ User deleted.');
    await load();
  };

  const btnSm = (color, label, onClick, disabled) => (
    <button onClick={onClick} disabled={disabled} style={{
      background: disabled ? '#1a1a35' : `${color}18`,
      border: `1px solid ${disabled ? '#2a2a50' : `${color}40`}`,
      color: disabled ? '#3a3a5a' : color,
      borderRadius: 7, padding: '4px 12px',
      cursor: disabled ? 'not-allowed' : 'pointer',
      fontSize: 12, fontWeight: 600, transition: 'all 0.15s',
    }}>{label}</button>
  );

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
        <h2 style={{ color: '#e2e2f0', fontSize: 20, fontWeight: 700, margin: 0 }}>👥 Users</h2>
        <input
          value={search} onChange={e => setSearch(e.target.value)}
          placeholder="Search by username or email…"
          style={{ background: '#0d0d1f', border: '1px solid #2a2a50', borderRadius: 10, padding: '8px 14px', color: '#e2e2f0', fontSize: 13, outline: 'none', width: 280 }}
        />
      </div>

      {actionMsg && <div style={{ background: '#34d39915', border: '1px solid #34d39940', borderRadius: 10, padding: '10px 14px', color: '#34d399', marginBottom: 16, fontSize: 14 }}>{actionMsg}</div>}

      {loading ? <Spinner /> : (
        <div style={{ background: '#12122a', border: '1px solid #1e1e3f', borderRadius: 16, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
              <thead>
                <tr style={{ background: '#0d0d1f' }}>
                  {['ID', 'Username', 'Email', 'Role', 'Streak', 'Submissions', 'Joined', 'Actions'].map(h => (
                    <th key={h} style={{ padding: '12px 16px', textAlign: 'left', color: '#8b8ba7', fontWeight: 600, fontSize: 11, letterSpacing: '0.05em', textTransform: 'uppercase', borderBottom: '1px solid #1e1e3f', whiteSpace: 'nowrap' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map(u => {
                  const isSelf = u.id === currentUserId;
                  return (
                    <tr key={u.id} style={{ borderBottom: '1px solid #1a1a35', transition: 'background 0.15s' }}
                      onMouseEnter={e => e.currentTarget.style.background = '#1a1a35'}
                      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}>
                      <td style={{ padding: '12px 16px', color: '#8b8ba7', fontVariantNumeric: 'tabular-nums' }}>{u.id}</td>
                      <td style={{ padding: '12px 16px', color: '#e2e2f0', fontWeight: 600 }}>
                        {u.username} {isSelf && <span style={{ color: '#a78bfa', fontSize: 11 }}>(you)</span>}
                      </td>
                      <td style={{ padding: '12px 16px', color: '#8b8ba7' }}>{u.email}</td>
                      <td style={{ padding: '12px 16px' }}>
                        <Badge label={u.role} colors={ROLE_COLORS[u.role] || ROLE_COLORS.USER} />
                      </td>
                      <td style={{ padding: '12px 16px', color: '#ffc01e', fontVariantNumeric: 'tabular-nums' }}>🔥 {u.streak}</td>
                      <td style={{ padding: '12px 16px', color: '#60a5fa', fontVariantNumeric: 'tabular-nums' }}>{u.totalSubmissions}</td>
                      <td style={{ padding: '12px 16px', color: '#8b8ba7', whiteSpace: 'nowrap' }}>
                        {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <div style={{ display: 'flex', gap: 6 }}>
                          {btnSm('#a78bfa', u.role === 'ADMIN' ? '→ User' : '→ Admin', () => handleRoleToggle(u), isSelf)}
                          {btnSm('#ff375f', 'Delete', () => setConfirm({ id: u.id, username: u.username }), isSelf)}
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {filtered.length === 0 && (
                  <tr><td colSpan={8} style={{ padding: 40, textAlign: 'center', color: '#8b8ba7' }}>No users found.</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {confirm && (
        <ConfirmDialog
          message={`Delete user "${confirm.username}"? All their submissions will be removed.`}
          onConfirm={handleDelete}
          onCancel={() => setConfirm(null)}
        />
      )}
    </div>
  );
}

// ─── Main Admin Page ──────────────────────────────────────────────────────────

export default function AdminPage() {
  const [tab,          setTab]          = useState('dashboard');
  const [stats,        setStats]        = useState(null);
  const [problemStats, setProblemStats] = useState(null);

  // Read current user id from localStorage (set during login)
  const currentUser = (() => { try { return JSON.parse(localStorage.getItem('user')); } catch { return null; } })();

  useEffect(() => {
    Promise.all([
      adminApi.getStats(),
      problemsApi.getStats(),
    ]).then(([s, ps]) => {
      setStats(s.data);
      setProblemStats(ps.data);
    }).catch(() => {});
  }, []);

  const tabs = [
    { key: 'dashboard', label: '📊 Dashboard' },
    { key: 'problems',  label: '📝 Problems'  },
    { key: 'users',     label: '👥 Users'     },
  ];

  return (
    <>
      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
        * { box-sizing: border-box; }
      `}</style>
      <div style={{ minHeight: '100vh', background: '#0d0d1f', fontFamily: "'Inter', 'Segoe UI', sans-serif" }}>
        {/* Header */}
        <div style={{ background: 'linear-gradient(135deg, #12122a 0%, #1a0a2e 100%)', borderBottom: '1px solid #2a2a50', padding: '24px 0' }}>
          <div style={{ maxWidth: 1200, margin: '0 auto', padding: '0 24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ width: 44, height: 44, borderRadius: 12, background: 'linear-gradient(135deg, #7c3aed, #a78bfa)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 22 }}>🛡️</div>
              <div>
                <h1 style={{ color: '#e2e2f0', fontSize: 22, fontWeight: 800, margin: 0, lineHeight: 1.2 }}>Admin Panel</h1>
                <p style={{ color: '#8b8ba7', fontSize: 13, margin: 0 }}>Manage problems and users</p>
              </div>
            </div>
          </div>
        </div>

        <div style={{ maxWidth: 1200, margin: '0 auto', padding: '32px 24px' }}>
          {/* Tab navigation */}
          <div style={{ display: 'flex', gap: 4, background: '#12122a', border: '1px solid #1e1e3f', borderRadius: 14, padding: 6, marginBottom: 32, width: 'fit-content' }}>
            {tabs.map(t => (
              <button
                key={t.key}
                onClick={() => setTab(t.key)}
                style={{
                  background:    tab === t.key ? 'linear-gradient(135deg, #7c3aed, #a78bfa)' : 'transparent',
                  border:        'none',
                  color:         tab === t.key ? '#fff' : '#8b8ba7',
                  borderRadius:  10,
                  padding:       '9px 22px',
                  cursor:        'pointer',
                  fontSize:      14,
                  fontWeight:    tab === t.key ? 700 : 500,
                  transition:    'all 0.2s',
                  whiteSpace:    'nowrap',
                }}
              >{t.label}</button>
            ))}
          </div>

          {/* Tab content */}
          {tab === 'dashboard' && <DashboardTab stats={stats} problemStats={problemStats} />}
          {tab === 'problems'  && <ProblemsTab />}
          {tab === 'users'     && <UsersTab currentUserId={currentUser?.id} />}
        </div>
      </div>
    </>
  );
}
