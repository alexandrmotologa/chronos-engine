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

  const dlqTbody = document.getElementById('dlq-tbody');
  const btnRefreshDlq = document.getElementById('btn-refresh-dlq');

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
    // Remove placeholder if present
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

    // Keep feed trimmed to 50 items
    while (eventFeed.children.length > 50) {
      eventFeed.removeChild(eventFeed.lastChild);
    }
  }

  btnClearFeed.addEventListener('click', () => {
    eventFeed.innerHTML = '<div class="event-item" style="color: var(--text-muted);">Event feed cleared.</div>';
  });

  // 2. Poll Cluster Status for KPIs
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
      }
    } catch (err) {
      console.warn('Status poll failed:', err);
    }
  }

  // 3. Handle Schedule Form Submission
  scheduleForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    btnSubmit.disabled = true;
    btnSubmit.textContent = 'Scheduling...';

    const delaySec = parseInt(document.getElementById('delay-sec').value, 10) || 5;
    const taskType = document.getElementById('task-type').value;
    const targetUrl = document.getElementById('target-url').value;
    const idKey = document.getElementById('idempotency-key').value.trim();
    const payloadStr = document.getElementById('task-payload').value;

    const scheduledDate = new Date(Date.now() + delaySec * 1000).toISOString();

    const requestBody = {
      type: taskType,
      target: targetUrl,
      scheduledTime: scheduledDate,
      payload: payloadStr,
      retryPolicy: {
        maxAttempts: 3,
        initialIntervalMs: 1000,
        multiplier: 2.0,
        jitterFactor: 0.2
      }
    };

    if (idKey) {
      requestBody.idempotencyKey = idKey;
    }

    try {
      const res = await fetch('/api/v1/tasks', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (res.ok) {
        const result = await res.json();
        btnSubmit.textContent = 'Enqueued!';
        setTimeout(() => {
          btnSubmit.textContent = 'Enqueue Delayed Task';
          btnSubmit.disabled = false;
        }, 1200);
      } else {
        const err = await res.json();
        alert('Failed to schedule task: ' + (err.detail || err.title || 'Unknown error'));
        btnSubmit.disabled = false;
        btnSubmit.textContent = 'Enqueue Delayed Task';
      }
    } catch (err) {
      alert('Network error scheduling task: ' + err.message);
      btnSubmit.disabled = false;
      btnSubmit.textContent = 'Enqueue Delayed Task';
    }
  });

  // 4. Fetch & Redrive Dead Letter Tasks
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

  // Initialize
  initSSE();
  fetchClusterStatus();
  fetchDLQ();
  setInterval(fetchClusterStatus, 2000);
});
