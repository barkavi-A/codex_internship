/* Vanilla JS Application Logic & Pure HTML5 Canvas Chart Engine */

const App = {
    token: sessionStorage.getItem('jwt_token') || null,
    user: JSON.parse(sessionStorage.getItem('user_info') || 'null'),

    init() {
        this.setupNavigation();
        if (this.token) {
            this.updateUserUI();
        }
    },

    setSession(token, user) {
        this.token = token;
        this.user = user;
        sessionStorage.setItem('jwt_token', token);
        sessionStorage.setItem('user_info', JSON.stringify(user));
        this.updateUserUI();
    },

    clearSession() {
        this.token = null;
        this.user = null;
        sessionStorage.removeItem('jwt_token');
        sessionStorage.removeItem('user_info');
        window.location.href = '/index.html';
    },

    updateUserUI() {
        const userNav = document.getElementById('user-nav');
        if (userNav && this.user) {
            userNav.innerHTML = `
                <span style="font-weight: 500; color: var(--text-secondary);">Hi, ${this.escapeHtml(this.user.name)}</span>
                <button onclick="App.clearSession()" class="btn btn-secondary btn-sm">Logout</button>
            `;
        }
    },

    setupNavigation() {
        const currentPage = window.location.pathname.split('/').pop() || 'index.html';
        document.querySelectorAll('.nav-link').forEach(link => {
            if (link.getAttribute('href') === currentPage) {
                link.classList.add('active');
            }
        });
    },

    async fetchApi(endpoint, options = {}) {
        const headers = options.headers || {};
        headers['Content-Type'] = 'application/json';
        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }

        const config = {
            ...options,
            headers
        };

        const response = await fetch(endpoint, config);

        if (response.status === 401) {
            this.clearSession();
            throw new Error('Session expired or unauthorized. Please log in.');
        }

        if (!response.ok) {
            let errorMsg = 'API Request Failed';
            try {
                const errData = await response.json();
                errorMsg = errData.message || errData.error || errorMsg;
            } catch (e) {}
            throw new Error(errorMsg);
        }

        if (response.status === 204) return null;
        return response.json();
    },

    showToast(message, type = 'info') {
        const toast = document.getElementById('toast');
        if (toast) {
            toast.textContent = message;
            toast.style.borderColor = type === 'error' ? 'var(--accent-rose)' : 'var(--accent-blue)';
            toast.style.display = 'block';
            setTimeout(() => {
                toast.style.display = 'none';
            }, 3500);
        }
    },

    escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    },

    // Pure HTML5 Canvas Chart Engine
    Charts: {
        drawLineChart(canvasId, labels, dataPoints, title = 'Clicks Over Time') {
            const canvas = document.getElementById(canvasId);
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            const dpr = window.devicePixelRatio || 1;
            const rect = canvas.getBoundingClientRect();

            canvas.width = rect.width * dpr;
            canvas.height = (rect.height || 260) * dpr;
            ctx.scale(dpr, dpr);

            const width = rect.width;
            const height = rect.height || 260;
            const padding = 40;

            ctx.clearRect(0, 0, width, height);

            if (!dataPoints || dataPoints.length === 0) {
                ctx.fillStyle = '#94a3b8';
                ctx.font = '14px Inter, sans-serif';
                ctx.textAlign = 'center';
                ctx.fillText('No click data available for the selected period', width / 2, height / 2);
                return;
            }

            const maxVal = Math.max(...dataPoints, 5);

            // Draw grid lines
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
            ctx.lineWidth = 1;
            for (let i = 0; i <= 4; i++) {
                const y = padding + (height - padding * 2) * (i / 4);
                ctx.beginPath();
                ctx.moveTo(padding, y);
                ctx.lineTo(width - padding, y);
                ctx.stroke();

                ctx.fillStyle = '#64748b';
                ctx.font = '11px Inter, sans-serif';
                ctx.textAlign = 'right';
                const labelVal = Math.round(maxVal * (1 - i / 4));
                ctx.fillText(labelVal, padding - 8, y + 4);
            }

            // Points step
            const xStep = (width - padding * 2) / Math.max(dataPoints.length - 1, 1);

            // Fill gradient area
            const gradient = ctx.createLinearGradient(0, padding, 0, height - padding);
            gradient.addColorStop(0, 'rgba(56, 189, 248, 0.35)');
            gradient.addColorStop(1, 'rgba(56, 189, 248, 0.0)');

            ctx.beginPath();
            ctx.moveTo(padding, height - padding);
            dataPoints.forEach((val, i) => {
                const x = padding + i * xStep;
                const y = height - padding - ((val / maxVal) * (height - padding * 2));
                ctx.lineTo(x, y);
            });
            ctx.lineTo(padding + (dataPoints.length - 1) * xStep, height - padding);
            ctx.closePath();
            ctx.fillStyle = gradient;
            ctx.fill();

            // Draw line
            ctx.beginPath();
            ctx.strokeStyle = '#38bdf8';
            ctx.lineWidth = 2.5;
            dataPoints.forEach((val, i) => {
                const x = padding + i * xStep;
                const y = height - padding - ((val / maxVal) * (height - padding * 2));
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            });
            ctx.stroke();

            // Draw X-axis labels (sub-sample if too many)
            ctx.fillStyle = '#94a3b8';
            ctx.font = '10px Inter, sans-serif';
            ctx.textAlign = 'center';
            const stepRatio = Math.ceil(labels.length / 7);
            labels.forEach((lbl, i) => {
                if (i % stepRatio === 0 || i === labels.length - 1) {
                    const x = padding + i * xStep;
                    const shortLabel = lbl.length > 10 ? lbl.substring(5) : lbl;
                    ctx.fillText(shortLabel, x, height - 12);
                }
            });
        },

        drawDoughnutChart(canvasId, items) {
            const canvas = document.getElementById(canvasId);
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            const dpr = window.devicePixelRatio || 1;
            const rect = canvas.getBoundingClientRect();

            canvas.width = rect.width * dpr;
            canvas.height = (rect.height || 220) * dpr;
            ctx.scale(dpr, dpr);

            const width = rect.width;
            const height = rect.height || 220;

            ctx.clearRect(0, 0, width, height);

            if (!items || items.length === 0) {
                ctx.fillStyle = '#94a3b8';
                ctx.font = '14px Inter, sans-serif';
                ctx.textAlign = 'center';
                ctx.fillText('No breakdown data', width / 2, height / 2);
                return;
            }

            const colors = ['#38bdf8', '#818cf8', '#34d399', '#fb7185', '#f59e0b', '#a855f7'];
            const total = items.reduce((sum, item) => sum + item.count, 0);

            const centerX = width / 3;
            const centerY = height / 2;
            const radius = Math.min(centerX, centerY) - 20;

            let startAngle = -Math.PI / 2;
            items.forEach((item, i) => {
                const sliceAngle = total > 0 ? (item.count / total) * 2 * Math.PI : 0;
                ctx.beginPath();
                ctx.arc(centerX, centerY, radius, startAngle, startAngle + sliceAngle);
                ctx.arc(centerX, centerY, radius * 0.55, startAngle + sliceAngle, startAngle, true);
                ctx.closePath();
                ctx.fillStyle = colors[i % colors.length];
                ctx.fill();
                startAngle += sliceAngle;
            });

            // Legend
            const legendX = width * 0.58;
            let legendY = 30;
            items.slice(0, 6).forEach((item, i) => {
                ctx.fillStyle = colors[i % colors.length];
                ctx.fillRect(legendX, legendY, 12, 12);

                ctx.fillStyle = '#f8fafc';
                ctx.font = '12px Inter, sans-serif';
                ctx.textAlign = 'left';
                const pct = total > 0 ? ((item.count / total) * 100).toFixed(1) : 0;
                ctx.fillText(`${item.name} (${pct}%)`, legendX + 20, legendY + 10);

                legendY += 24;
            });
        }
    }
};

document.addEventListener('DOMContentLoaded', () => App.init());
