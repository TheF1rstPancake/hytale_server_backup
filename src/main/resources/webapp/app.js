const API_BASE = '/api/backups';
let autoRefreshInterval = null;
let restoreEnabled = false;
let currentConfig = null;

const TIER_DISPLAY = {
    GRANDFATHER: { label: 'Archive', badge: 'archive' },
    FATHER:      { label: 'Daily',   badge: 'daily' },
    SON:         { label: 'Snapshot', badge: 'snapshot' }
};

async function loadBackups() {
    try {
        const response = await fetch(API_BASE);
        const data = await response.json();

        if (data.success) {
            restoreEnabled = data.allowRestore === true;
            currentConfig = data.config || null;
            updateStats(data.stats);
            updateTierHeaders();
            renderConfigPanel();
            renderBackupTable('grandfatherTable', data.grouped.GRANDFATHER || [], 'GRANDFATHER');
            renderBackupTable('fatherTable', data.grouped.FATHER || [], 'FATHER');
            renderBackupTable('sonTable', data.grouped.SON || [], 'SON');
        } else {
            showNotification('Failed to load backups: ' + data.error, 'error');
        }
    } catch (error) {
        showNotification('Error loading backups: ' + error.message, 'error');
    }
}

function updateStats(stats) {
    document.getElementById('totalBackups').textContent = stats.totalBackups || 0;
    document.getElementById('totalSize').textContent = formatBytes(stats.totalSizeBytes || 0);
    document.getElementById('lastBackup').textContent = stats.lastBackup
        ? formatDate(new Date(stats.lastBackup))
        : 'Never';
    document.getElementById('sonCount').textContent = stats.sonCount || 0;
    document.getElementById('fatherCount').textContent = stats.fatherCount || 0;
    document.getElementById('grandfatherCount').textContent = stats.grandfatherCount || 0;
}

function formatInterval(minutes) {
    if (minutes < 60) return minutes + ' min';
    if (minutes < 1440) return (minutes / 60) + 'h';
    if (minutes < 10080) return (minutes / 1440) + 'd';
    return (minutes / 10080) + 'w';
}

function updateTierHeaders() {
    if (!currentConfig) return;

    const snap = currentConfig.snapshots;
    const daily = currentConfig.dailies;
    const arch = currentConfig.archives;

    if (snap) {
        document.getElementById('snapshotTitle').innerHTML =
            'Snapshots <span class="tier-config-detail">every ' +
            formatInterval(snap.intervalMinutes) + ', keeping ' +
            snap.retentionCount + '</span>';
    }
    if (daily) {
        document.getElementById('dailyTitle').innerHTML =
            'Dailies <span class="tier-config-detail">every ' +
            formatInterval(daily.intervalMinutes) + ', keeping ' +
            daily.retentionCount + '</span>';
    }
    if (arch) {
        document.getElementById('archiveTitle').innerHTML =
            'Archives <span class="tier-config-detail">every ' +
            formatInterval(arch.intervalMinutes) + ', keeping ' +
            arch.retentionCount + '</span>';
    }
}

function renderConfigPanel() {
    if (!currentConfig) return;

    const grid = document.getElementById('configGrid');
    const cfg = currentConfig;

    grid.innerHTML = `
        <div class="config-card">
            <h4>Snapshots</h4>
            <dl>
                <dt>Interval</dt><dd>Every ${formatInterval(cfg.snapshots.intervalMinutes)}</dd>
                <dt>Retention</dt><dd>${cfg.snapshots.retentionCount} backups</dd>
                <dt>Status</dt><dd>${cfg.snapshots.enabled ? 'Enabled' : 'Disabled'}</dd>
            </dl>
        </div>
        <div class="config-card">
            <h4>Dailies</h4>
            <dl>
                <dt>Interval</dt><dd>Every ${formatInterval(cfg.dailies.intervalMinutes)}</dd>
                <dt>Retention</dt><dd>${cfg.dailies.retentionCount} backups</dd>
                <dt>Status</dt><dd>${cfg.dailies.enabled ? 'Enabled' : 'Disabled'}</dd>
            </dl>
        </div>
        <div class="config-card">
            <h4>Archives</h4>
            <dl>
                <dt>Interval</dt><dd>Every ${formatInterval(cfg.archives.intervalMinutes)}</dd>
                <dt>Retention</dt><dd>${cfg.archives.retentionCount} backups</dd>
                <dt>Status</dt><dd>${cfg.archives.enabled ? 'Enabled' : 'Disabled'}</dd>
            </dl>
        </div>
        <div class="config-card">
            <h4>General</h4>
            <dl>
                <dt>Backup folder</dt><dd>${cfg.backupFolder}</dd>
                <dt>World folder</dt><dd>${cfg.worldFolder}</dd>
                <dt>Save before backup</dt><dd>${cfg.advanced.serverSaveBeforeBackup ? 'Yes' : 'No'}</dd>
                <dt>Async backup</dt><dd>${cfg.advanced.asyncBackup ? 'Yes' : 'No'}</dd>
            </dl>
        </div>
    `;
}

function renderBackupTable(tableId, backups, tier) {
    const container = document.getElementById(tableId);
    const display = TIER_DISPLAY[tier] || { label: tier, badge: tier.toLowerCase() };

    if (backups.length === 0) {
        container.innerHTML = '<div class="empty-state"><p>No backups in this tier</p></div>';
        return;
    }

    const table = document.createElement('table');
    table.innerHTML = `
        <thead>
            <tr>
                <th>Filename</th>
                <th>Created</th>
                <th>Size</th>
                <th>Tier</th>
                <th>Actions</th>
            </tr>
        </thead>
        <tbody>
            ${backups.map(backup => `
                <tr>
                    <td>${backup.filename}</td>
                    <td>${formatDate(new Date(backup.createdAt))}</td>
                    <td>${formatBytes(backup.sizeBytes)}</td>
                    <td><span class="badge badge-${display.badge}">${display.label}</span></td>
                    <td class="backup-actions">
                        <button class="btn btn-info" onclick="downloadBackup('${backup.filename}')">Download</button>
                        ${restoreEnabled ? `<button class="btn btn-warning" onclick="confirmRestore('${backup.filename}')">Restore</button>` : ''}
                        <button class="btn btn-danger" onclick="confirmDelete('${backup.filename}')">Delete</button>
                    </td>
                </tr>
            `).join('')}
        </tbody>
    `;

    container.innerHTML = '';
    container.appendChild(table);
}

async function createBackup() {
    if (!confirm('Create a new backup now?')) {
        return;
    }

    try {
        showNotification('Creating backup...', 'info');

        const response = await fetch(API_BASE + '/create', {
            method: 'POST'
        });

        const data = await response.json();

        if (data.success) {
            showNotification('Backup created successfully: ' + data.filename, 'success');
            loadBackups();
        } else {
            showNotification('Failed to create backup: ' + data.error, 'error');
        }
    } catch (error) {
        showNotification('Error creating backup: ' + error.message, 'error');
    }
}

function downloadBackup(filename) {
    const url = API_BASE + '/download/' + filename;
    window.location.href = url;
    showNotification('Downloading backup: ' + filename, 'success');
}

async function confirmRestore(filename) {
    if (!confirm(`Restore backup "${filename}"?\n\nWARNING: This will extract the backup to temp-restore folder. Manual server restart required.`)) {
        return;
    }

    try {
        showNotification('Restoring backup...', 'info');

        const response = await fetch(API_BASE + '/restore/' + filename, {
            method: 'POST'
        });

        const data = await response.json();

        if (data.success) {
            showNotification(data.message, 'success');
        } else {
            showNotification('Failed to restore backup: ' + data.error, 'error');
        }
    } catch (error) {
        showNotification('Error restoring backup: ' + error.message, 'error');
    }
}

async function confirmDelete(filename) {
    if (!confirm(`Delete backup "${filename}"?\n\nThis action cannot be undone.`)) {
        return;
    }

    try {
        showNotification('Deleting backup...', 'info');

        const response = await fetch(API_BASE + '/delete/' + filename, {
            method: 'DELETE'
        });

        const data = await response.json();

        if (data.success) {
            showNotification('Backup deleted successfully', 'success');
            loadBackups();
        } else {
            showNotification('Failed to delete backup: ' + data.error, 'error');
        }
    } catch (error) {
        showNotification('Error deleting backup: ' + error.message, 'error');
    }
}

function showNotification(message, type = 'info') {
    const notification = document.getElementById('notification');
    notification.textContent = message;
    notification.className = 'notification ' + type;

    setTimeout(() => {
        notification.className = 'notification hidden';
    }, 5000);
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';

    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
}

function formatDate(date) {
    return date.toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

// Config panel toggle
document.getElementById('configToggle').addEventListener('click', function () {
    const panel = document.getElementById('configPanel');
    panel.classList.toggle('hidden');
    this.classList.toggle('open');
    if (!panel.classList.contains('hidden')) {
        this.style.borderRadius = '8px 8px 0 0';
    } else {
        this.style.borderRadius = '8px';
    }
});

document.getElementById('createBackup').addEventListener('click', createBackup);
document.getElementById('refreshBackups').addEventListener('click', loadBackups);

loadBackups();

autoRefreshInterval = setInterval(loadBackups, 30000);

window.addEventListener('beforeunload', () => {
    if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval);
    }
});
