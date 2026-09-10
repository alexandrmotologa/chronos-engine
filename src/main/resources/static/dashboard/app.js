// Chronos Engine Dashboard Client

document.addEventListener('DOMContentLoaded', () => {
  const connectionPill = document.getElementById('connection-pill');
  const connectionText = document.getElementById('connection-text');
  const nodeBadge = document.getElementById('node-badge');

  const kpiPending = document.getElementById('kpi-pending');
  const kpiDrift = document.getElementById('kpi-drift');
  const kpiPartitions = document.getElementById('kpi-partitions');
  const kpiDeadletters = document.getElementById('kpi-deadletters');

  const eventFeed = document.getElementById('event-feed');
  const btnClearFeed = document.getElementById('btn-clear-feed');
  const scheduleForm = document.getElementById('schedule-form');
  const btnSubmit = document.getElementById('btn-submit');
  const btnBulkGenerate = document.getElementById('btn-bulk-generate');

  const cancelTagInput = document.getElementById('cancel-tag-input');
  const btnCancelTag = document.getElementById('btn-cancel-tag');

  const ganttSvg = document.getElementById('gantt-svg');
  const btnRefreshTimeline = document.getElementById('btn-refresh-timeline');

  const partitionGrid = document.getElementById('partition-grid');

  const activeTasksTbody = document.getElementById('active-tasks-tbody');
  const btnRefreshActive = document.getElementById('btn-refresh-active');

  const dlqTbody = document.getElementById('dlq-tbody');
  const btnRefreshDlq = document.getElementById('btn-refresh-dlq');

  // Initialize 256 Virtual Partition Grid
  function initPartitionGrid() {
    partitionGrid.innerHTML = '';
    for (let i = 0; i < 256; i++) {
      const cell = document.createElement('div');
      cell.className = 'partition-cell';
      cell.dataset.partition = i;
      cell.title = `Partition #${i}: Idle / Unassigned`;
      partitionGrid.appendChild(cell);
    }
  }

  function updatePartitions(ownedList) {
    const ownedSet = new Set(ownedList || []);
    const cells = partitionGrid.querySelectorAll('.partition-cell');
    cells.forEach((cell, idx) => {
      if (ownedSet.has(idx)) {
        cell.classList.add('owned');
        cell.title = `Partition #${idx}: Owned by Current Node`;
      } else {
        cell.classList.remove('owned');
        cell.title = `Partition #${idx}: Idle / Unassigned`;
      }
    });
  }

  // 1. Setup Server-Sent Events (SSE)
  let eventSource = null;

  function initSSE() {
    eventSource = new EventSource('/api/v1/tasks/live');

    eventSource.addEventListener('open', () => {
      connectionPill.classList.remove('disconnected');
      connectionText.textContent = 'Live SSE Connected';
    });

    eventSource.addEventListener('CONNECTED', (e) => {
      try {
        const data = JSON.parse(e.data);
        nodeBadge.textContent = `Worker Node: ${data.nodeId}`;
      } catch (err) {}
    });

    eventSource.addEventListener('TASK_EVENT', (e) => {
      try {
        const event = JSON.parse(e.data);
        addEventToFeed(event);
        fetchTimeline();
        fetchActiveTasks();
      } catch (err) {
        console.error('Failed to parse task event:', err);
      }
    });

    eventSource.addEventListener('error', () => {
      connectionPill.classList.add('disconnected');
      connectionText.textContent = 'Reconnecting...';
    });
  }

  function addEventToFeed(event) {
    if (eventFeed.children.length === 1 && eventFeed.children[0].textContent.includes('Listening')) {
      eventFeed.innerHTML = '';
    }

    const item = document.createElement('div');
    item.className = 'event-item';

    let badgeClass = 'badge-scheduled';
    if (event.eventType.includes('Acquired')) badgeClass = 'badge-acquired';
    if (event.eventType.includes('Executed')) badgeClass = 'badge-executed';
    if (event.eventType.includes('Failed')) badgeClass = 'badge-failed';
    if (event.eventType.includes('DeadLettered')) badgeClass = 'badge-deadletter';
    if (event.eventType.includes('Paused')) badgeClass = 'badge-paused';

    const shortId = event.taskId ? event.taskId.substring(0, 8) + '...' : 'Unknown';
    const timeFormatted = new Date(event.occurredAt || Date.now()).toLocaleTimeString();

    item.innerHTML = `
      <div class="event-meta">
        <span class="event-task-id">${shortId}</span>
        <span class="event-time">${timeFormatted}</span>
      </div>
      <span class="event-badge ${badgeClass}">${event.eventType.replace('Event', '')}</span>
    `;

    eventFeed.prepend(item);

    while (eventFeed.children.length > 50) {
      eventFeed.removeChild(eventFeed.lastChild);
    }
  }

  btnClearFeed.addEventListener('click', () => {
    eventFeed.innerHTML = '<div class="event-item" style="color: var(--text-muted);">Event feed cleared.</div>';
  });

  // 2. Poll Cluster Status for KPIs & Partitions
  async function fetchClusterStatus() {
    try {
      const res = await fetch('/api/v1/cluster/status');
      if (res.ok) {
        const data = await res.json();
        kpiPending.textContent = data.pendingTimeouts || 0;
        kpiDrift.textContent = `${data.averageDriftMs || 0.0} ms`;
        kpiPartitions.textContent = data.ownedPartitionsCount || 0;
        kpiDeadletters.textContent = data.deadLettersCount || 0;
        if (data.nodeId) {
          nodeBadge.textContent = `Worker Node: ${data.nodeId}`;
        }
        updatePartitions(data.ownedPartitions);
      }
    } catch (err) {
      console.warn('Status poll failed:', err);
    }
  }

  // 3. Render Gantt SVG Timeline
  async function fetchTimeline() {
    try {
      const res = await fetch('/api/v1/tasks/timeline?windowSec=60');
      if (!res.ok) return;
      const tasks = await res.json();
      renderGantt(tasks);
    } catch (err) {
      console.warn('Timeline fetch failed:', err);
    }
  }

  function renderGantt(tasks) {
    const width = ganttSvg.clientWidth || 800;
    const height = Math.max(160, (tasks.length + 1) * 28 + 20);
    ganttSvg.setAttribute('height', height);
    ganttSvg.innerHTML = '';

    const now = Date.now();
    const windowMs = 60 * 1000;

    // Draw vertical grid lines for +15s, +30s, +45s, +60s
    for (let sec = 15; sec <= 60; sec += 15) {
      const x = (sec / 60) * width;
      const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
      line.setAttribute('x1', x);
      line.setAttribute('y1', 0);
      line.setAttribute('x2', x);
      line.setAttribute('y2', height);
      line.setAttribute('stroke', 'rgba(255,255,255,0.06)');
      line.setAttribute('stroke-dasharray', '4');
      ganttSvg.appendChild(line);
    }

    if (!tasks || tasks.length === 0) {
      const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      text.setAttribute('x', width / 2);
      text.setAttribute('y', 80);
      text.setAttribute('text-anchor', 'middle');
      text.setAttribute('fill', '#6b7280');
      text.setAttribute('font-size', '13');
      text.textContent = 'No tasks scheduled in the next 60 seconds.';
      ganttSvg.appendChild(text);
      return;
    }

    tasks.forEach((t, i) => {
      const schedTime = new Date(t.scheduledTime).getTime();
      const diffMs = schedTime - now;
      const ratio = Math.max(0, Math.min(1, diffMs / windowMs));
      const barX = ratio * (width - 120) + 10;
      const barY = i * 28 + 12;
      const barWidth = 100;
      const barHeight = 20;

      const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
      rect.setAttribute('x', barX);
      rect.setAttribute('y', barY);
      rect.setAttribute('width', barWidth);
      rect.setAttribute('height', barHeight);
      rect.setAttribute('class', 'gantt-bar');

      const fill = t.type === 'KAFKA' ? '#f59e0b' : '#06b6d4';
      rect.setAttribute('fill', fill);
      rect.setAttribute('fill-opacity', '0.85');

      const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
      label.setAttribute('x', barX + 6);
      label.setAttribute('y', barY + 14);
      label.setAttribute('fill', '#ffffff');
      label.setAttribute('font-size', '10');
      label.setAttribute('font-family', 'ui-monospace, monospace');
      label.textContent = `${t.id.substring(0, 6)} (+${Math.max(0, Math.round(diffMs / 1000))}s)`;

      rect.addEventListener('click', () => {
        alert(`Task ${t.id}\nStatus: ${t.status}\nTarget: ${t.target}\nScheduled: ${t.scheduledTime}`);
      });

      ganttSvg.appendChild(rect);
      ganttSvg.appendChild(label);
    });
  }

  // 4. Fetch & Render Active Upcoming Tasks
  async function fetchActiveTasks() {
    try {
      const res = await fetch('/api/v1/tasks/timeline?windowSec=120');
      if (!res.ok) return;
      const tasks = await res.json();
      renderActiveTasks(tasks);
    } catch (err) {
      console.warn('Active tasks fetch failed:', err);
    }
  }

  function renderActiveTasks(tasks) {
    if (!tasks || tasks.length === 0) {
      activeTasksTbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">No upcoming tasks scheduled in the immediate horizon.</td></tr>';
      return;
    }

    activeTasksTbody.innerHTML = tasks.map(t => {
      const isPaused = t.status === 'PAUSED';
      const pauseResumeBtn = isPaused
        ? `<button class="btn btn-secondary" style="font-size: 11px; padding: 3px 6px;" onclick="window.resumeTask('${t.id}')">Resume</button>`
        : `<button class="btn btn-secondary" style="font-size: 11px; padding: 3px 6px;" onclick="window.pauseTask('${t.id}')">Pause</button>`;

      const tagBadge = (t.tags && t.tags.length > 0)
        ? t.tags.map(tag => `<span style="background: rgba(255,255,255,0.08); padding: 2px 6px; border-radius: 4px; font-size: 10px;">${tag}</span>`).join(' ')
        : '<span style="color: var(--text-muted); font-size: 11px;">-</span>';

      return `
        <tr>
          <td>${t.id.substring(0, 8)}...</td>
          <td><span class="event-badge badge-${t.status.toLowerCase()}">${t.status}</span></td>
          <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${t.target}</td>
          <td>${new Date(t.scheduledTime).toLocaleTimeString()}</td>
          <td>${tagBadge}</td>
          <td style="display: flex; gap: 4px;">
            ${pauseResumeBtn}
            <button class="btn btn-secondary" style="font-size: 11px; padding: 3px 6px;" onclick="window.rescheduleTask('${t.id}')">+15s</button>
            <button class="btn btn-secondary" style="font-size: 11px; padding: 3px 6px;" onclick="window.fireTaskNow('${t.id}')">Fire</button>
            <button class="btn btn-danger" style="font-size: 11px; padding: 3px 6px;" onclick="window.cancelTask('${t.id}')">X</button>
          </td>
        </tr>
      `;
    }).join('');
  }

  // Window Action Handlers
  window.pauseTask = async function(id) {
    await fetch(`/api/v1/tasks/${id}/pause`, { method: 'POST' });
    fetchActiveTasks();
    fetchTimeline();
  };

  window.resumeTask = async function(id) {
    await fetch(`/api/v1/tasks/${id}/resume`, { method: 'POST' });
    fetchActiveTasks();
    fetchTimeline();
  };

  window.rescheduleTask = async function(id) {
    const newTime = new Date(Date.now() + 15 * 1000).toISOString();
    await fetch(`/api/v1/tasks/${id}/reschedule`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ newScheduledTime: newTime })
    });
    fetchActiveTasks();
    fetchTimeline();
  };

  window.fireTaskNow = async function(id) {
    await fetch(`/api/v1/tasks/${id}/fire-now`, { method: 'POST' });
    fetchActiveTasks();
    fetchTimeline();
  };

  window.cancelTask = async function(id) {
    await fetch(`/api/v1/tasks/${id}`, { method: 'DELETE' });
    fetchActiveTasks();
    fetchTimeline();
  };

  // 5. Bulk Generation Button
  btnBulkGenerate.addEventListener('click', async () => {
    btnBulkGenerate.disabled = true;
    btnBulkGenerate.textContent = 'Generating 10...';

    const tasks = [];
    const now = Date.now();
    for (let i = 1; i <= 10; i++) {
      tasks.push({
        type: 'WEBHOOK',
        target: 'https://httpbin.org/post',
        scheduledTime: new Date(now + (i * 4 + 5) * 1000).toISOString(),
        payload: JSON.stringify({ batchId: 'batch-demo', item: i }),
        tags: ['bulk-demo', `item-${i}`]
      });
    }

    try {
      const res = await fetch('/api/v1/tasks/bulk', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tasks })
      });

      if (res.ok) {
        btnBulkGenerate.textContent = '10 Queued!';
        setTimeout(() => {
          btnBulkGenerate.textContent = 'Generate 10 Bulk Tasks';
          btnBulkGenerate.disabled = false;
        }, 1200);
        fetchActiveTasks();
        fetchTimeline();
      } else {
        alert('Bulk generation failed');
        btnBulkGenerate.disabled = false;
        btnBulkGenerate.textContent = 'Generate 10 Bulk Tasks';
      }
    } catch (err) {
      alert('Network error: ' + err.message);
      btnBulkGenerate.disabled = false;
      btnBulkGenerate.textContent = 'Generate 10 Bulk Tasks';
    }
  });

  // 6. Bulk Cancellation by Tag
  btnCancelTag.addEventListener('click', async () => {
    const tag = cancelTagInput.value.trim();
    if (!tag) {
      alert('Please enter a tag name to cancel.');
      return;
    }

    try {
      const res = await fetch(`/api/v1/tasks?tag=${encodeURIComponent(tag)}`, {
        method: 'DELETE'
      });

      if (res.ok) {
        const result = await res.json();
        alert(`Cancelled ${result.cancelledCount} tasks tagged with '${tag}'`);
        fetchActiveTasks();
        fetchTimeline();
      } else {
        alert('Tag cancellation failed');
      }
    } catch (err) {
      alert('Error cancelling by tag: ' + err.message);
    }
  });

  // 7. Schedule Form
  scheduleForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    btnSubmit.disabled = true;
    btnSubmit.textContent = 'Scheduling...';

    const delaySec = parseInt(document.getElementById('delay-sec').value, 10) || 10;
    const taskType = document.getElementById('task-type').value;
    const targetUrl = document.getElementById('target-url').value;
    const idKey = document.getElementById('idempotency-key').value.trim();
    const tagsStr = document.getElementById('task-tags').value.trim();
    const payloadStr = document.getElementById('task-payload').value;

    const scheduledDate = new Date(Date.now() + delaySec * 1000).toISOString();
    const tags = tagsStr ? tagsStr.split(',').map(s => s.trim()).filter(Boolean) : [];

    const requestBody = {
      type: taskType,
      target: targetUrl,
      scheduledTime: scheduledDate,
      payload: payloadStr,
      tags: tags,
      retryPolicy: {
        maxAttempts: 3,
        initialIntervalMs: 1000,
        multiplier: 2.0,
        jitterFactor: 0.2
      }
    };

    if (idKey) requestBody.idempotencyKey = idKey;

    try {
      const res = await fetch('/api/v1/tasks', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (res.ok) {
        btnSubmit.textContent = 'Enqueued!';
        setTimeout(() => {
          btnSubmit.textContent = 'Enqueue Single Task';
          btnSubmit.disabled = false;
        }, 1200);
        fetchActiveTasks();
        fetchTimeline();
      } else {
        const err = await res.json();
        alert('Failed: ' + (err.detail || err.title || 'Unknown error'));
        btnSubmit.disabled = false;
        btnSubmit.textContent = 'Enqueue Single Task';
      }
    } catch (err) {
      alert('Network error: ' + err.message);
      btnSubmit.disabled = false;
      btnSubmit.textContent = 'Enqueue Single Task';
    }
  });

  // 8. DLQ Console
  async function fetchDLQ() {
    try {
      const res = await fetch('/api/v1/dlq?page=0&size=10');
      if (res.ok) {
        const data = await res.json();
        renderDLQ(data.tasks || []);
      }
    } catch (err) {
      console.warn('Failed to fetch DLQ:', err);
    }
  }

  function renderDLQ(tasks) {
    if (!tasks || tasks.length === 0) {
      dlqTbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">No failed tasks in dead letter queue.</td></tr>';
      return;
    }

    dlqTbody.innerHTML = tasks.map(t => `
      <tr>
        <td>${t.id.substring(0, 8)}...</td>
        <td style="max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${t.target}</td>
        <td>${t.retryCount} / ${t.maxAttempts}</td>
        <td>${new Date(t.createdAt).toLocaleTimeString()}</td>
        <td>
          <button class="btn btn-secondary" style="font-size: 11px; padding: 4px 8px;" onclick="window.redriveTask('${t.id}')">
            Redrive
          </button>
        </td>
      </tr>
    `).join('');
  }

  window.redriveTask = async function(taskId) {
    try {
      const res = await fetch(`/api/v1/dlq/${taskId}/redrive`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      if (res.ok) {
        fetchDLQ();
        fetchClusterStatus();
      } else {
        alert('Redrive failed for task ' + taskId);
      }
    } catch (err) {
      alert('Error redriving task: ' + err.message);
    }
  };

  btnRefreshDlq.addEventListener('click', fetchDLQ);
  btnRefreshTimeline.addEventListener('click', fetchTimeline);
  btnRefreshActive.addEventListener('click', fetchActiveTasks);

  // Initialize
  initPartitionGrid();
  initSSE();
  fetchClusterStatus();
  fetchTimeline();
  fetchActiveTasks();
  fetchDLQ();
  setInterval(fetchClusterStatus, 2500);
  setInterval(fetchTimeline, 5000);
});
