// FinTrack Application Logic

(function () {
    'use strict';

    // State
    const state = {
        token: sessionStorage.getItem('token') || null,
        user: JSON.parse(sessionStorage.getItem('user') || 'null'),
        categories: [],
        budgets: [],
        transactions: [],
        currentPage: 0,
        pageSize: 20,
        totalPages: 0,
        totalElements: 0,
        currentTab: 'dashboard'
    };

    // Helper: XSS escaping
    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // Helper: Toast Notifications
    function showToast(message, type = 'info') {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.innerHTML = `<span>${escapeHtml(message)}</span>`;
        container.appendChild(toast);
        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(100%)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    // API Client with Auth and 401 handling
    async function apiRequest(endpoint, options = {}) {
        const headers = options.headers || {};
        if (state.token) {
            headers['Authorization'] = `Bearer ${state.token}`;
        }
        if (!(options.body instanceof FormData)) {
            headers['Content-Type'] = 'application/json';
        }

        try {
            const response = await fetch(endpoint, { ...options, headers });

            if (response.status === 401) {
                if (state.token) {
                    showToast('Session expired. Please log in again.', 'warning');
                    logout();
                }
                const err = await response.json().catch(() => ({ message: 'Unauthorized' }));
                throw new Error(err.message || 'Unauthorized');
            }

            if (response.status === 204) {
                return null;
            }

            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('text/csv')) {
                const blob = await response.blob();
                return blob;
            }

            const data = await response.json().catch(() => null);
            if (!response.ok) {
                let msg = (data && data.message) ? data.message : `Request failed (${response.status})`;
                if (data && data.fieldErrors && data.fieldErrors.length > 0) {
                    msg = data.fieldErrors.map(fe => `${fe.field}: ${fe.message}`).join(', ');
                }
                throw new Error(msg);
            }
            return data;
        } catch (error) {
            throw error;
        }
    }

    // AUTH FUNCTIONS
    function setSession(token, user) {
        state.token = token;
        state.user = user;
        sessionStorage.setItem('token', token);
        sessionStorage.setItem('user', JSON.stringify(user));
        updateAuthUI();
    }

    function logout() {
        state.token = null;
        state.user = null;
        sessionStorage.removeItem('token');
        sessionStorage.removeItem('user');
        updateAuthUI();
    }

    function updateAuthUI() {
        const authSection = document.getElementById('auth-section');
        const appSection = document.getElementById('app-section');

        if (state.token && state.user) {
            authSection.classList.add('hidden');
            appSection.classList.remove('hidden');

            document.getElementById('user-name-display').textContent = state.user.name || 'User';
            document.getElementById('user-currency-display').textContent = state.user.currency || 'INR';
            document.getElementById('user-avatar').textContent = (state.user.name || 'U').charAt(0).toUpperCase();

            // Load initial app data
            loadCategories().then(() => {
                switchTab('dashboard');
            });
        } else {
            authSection.classList.remove('hidden');
            appSection.classList.add('hidden');
        }
    }

    // TAB NAVIGATION
    function switchTab(tabName) {
        state.currentTab = tabName;
        document.querySelectorAll('.nav-tab').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tabName);
        });
        document.querySelectorAll('.content-view').forEach(view => {
            view.classList.toggle('active', view.id === `view-${tabName}`);
        });

        if (tabName === 'dashboard') {
            loadDashboard();
        } else if (tabName === 'transactions') {
            loadTransactions();
        } else if (tabName === 'categories') {
            renderCategoriesView();
        } else if (tabName === 'budgets') {
            loadBudgetsView();
        }
    }

    // CATEGORIES
    async function loadCategories() {
        try {
            state.categories = await apiRequest('/api/categories');
            populateCategoryDropdowns();
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    function populateCategoryDropdowns() {
        const txCatSelect = document.getElementById('tx-category');
        const filterCatSelect = document.getElementById('filter-category');
        const budgetCatSelect = document.getElementById('budget-category');

        const currentTxType = document.getElementById('tx-type').value;

        // Populate Transaction Modal Category dropdown filtered by type
        txCatSelect.innerHTML = '';
        state.categories
            .filter(c => c.type === currentTxType)
            .forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = c.name;
                txCatSelect.appendChild(opt);
            });

        // Filter Bar Category dropdown
        filterCatSelect.innerHTML = '<option value="">All Categories</option>';
        state.categories.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = `${c.name} (${c.type})`;
            filterCatSelect.appendChild(opt);
        });

        // Budget Modal Category dropdown (Expense only)
        budgetCatSelect.innerHTML = '<option value="">Overall Monthly Budget</option>';
        state.categories
            .filter(c => c.type === 'EXPENSE')
            .forEach(c => {
                const opt = document.createElement('option');
                opt.value = c.id;
                opt.textContent = c.name;
                budgetCatSelect.appendChild(opt);
            });
    }

    // DASHBOARD
    async function loadDashboard() {
        const fromInput = document.getElementById('dash-from').value;
        const toInput = document.getElementById('dash-to').value;

        let query = '';
        if (fromInput && toInput) {
            query = `?from=${fromInput}&to=${toInput}`;
        }

        try {
            // Load summary
            const summary = await apiRequest(`/api/reports/summary${query}`);
            const currency = (state.user && state.user.currency) || '₹';

            document.getElementById('stat-income-val').textContent = `${currency} ${formatNumber(summary.totalIncome)}`;
            document.getElementById('stat-count-val').textContent = `${summary.transactionCount} transactions`;

            document.getElementById('stat-expense-val').textContent = `${currency} ${formatNumber(summary.totalExpense)}`;
            document.getElementById('stat-daily-avg-val').textContent = `Avg daily: ${currency} ${formatNumber(summary.averageDailyExpense)}`;

            document.getElementById('stat-net-val').textContent = `${currency} ${formatNumber(summary.netSavings)}`;
            document.getElementById('stat-highest-expense-val').textContent = `Peak expense: ${currency} ${formatNumber(summary.highestExpense)}`;

            document.getElementById('stat-rate-val').textContent = `${summary.savingsRate.toFixed(1)}%`;

            // Load category breakdown for doughnut chart
            const catReport = await apiRequest(`/api/reports/by-category${query}&type=EXPENSE`);
            drawCategoryDoughnut(catReport, summary.totalExpense, currency);

            // Load monthly trend for bar chart
            const year = new Date().getFullYear();
            const monthlyReport = await apiRequest(`/api/reports/monthly?year=${year}`);
            drawMonthlyBarChart(monthlyReport);

            // Load current month budget status
            const now = new Date();
            const currentYm = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
            const budgetStatuses = await apiRequest(`/api/budgets/status?month=${currentYm}`);
            renderDashboardBudgets(budgetStatuses, currency);
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    function formatNumber(val) {
        if (val === null || val === undefined) return '0.00';
        return Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    // HAND-DRAWN DOUGHNUT CHART
    function drawCategoryDoughnut(data, totalExpense, currency) {
        const canvas = document.getElementById('categoryCanvas');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        const legend = document.getElementById('category-legend');
        legend.innerHTML = '';

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        const centerX = canvas.width / 2;
        const centerY = canvas.height / 2;
        const radius = Math.min(centerX, centerY) - 25;
        const innerRadius = radius * 0.62;

        if (!data || data.length === 0 || Number(totalExpense) === 0) {
            ctx.beginPath();
            ctx.arc(centerX, centerY, radius, 0, 2 * Math.PI);
            ctx.fillStyle = '#1E293B';
            ctx.fill();

            ctx.fillStyle = '#64748B';
            ctx.font = '14px sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('No Expense Data', centerX, centerY);
            return;
        }

        let startAngle = -0.5 * Math.PI;

        data.forEach(item => {
            const sliceAngle = (Number(item.amount) / Number(totalExpense)) * 2 * Math.PI;
            const endAngle = startAngle + sliceAngle;

            ctx.beginPath();
            ctx.arc(centerX, centerY, radius, startAngle, endAngle);
            ctx.arc(centerX, centerY, innerRadius, endAngle, startAngle, true);
            ctx.closePath();
            ctx.fillStyle = item.categoryColor || '#6366F1';
            ctx.fill();

            startAngle = endAngle;

            // Build legend item
            const div = document.createElement('div');
            div.className = 'legend-item';
            div.innerHTML = `
                <span class="legend-color" style="background:${escapeHtml(item.categoryColor)}"></span>
                <span>${escapeHtml(item.categoryName)} (${item.percentage.toFixed(1)}%)</span>
            `;
            legend.appendChild(div);
        });

        // Center cutout & text
        ctx.beginPath();
        ctx.arc(centerX, centerY, innerRadius - 2, 0, 2 * Math.PI);
        ctx.fillStyle = '#0F172A';
        ctx.fill();

        ctx.fillStyle = '#94A3B8';
        ctx.font = '12px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('TOTAL EXPENSE', centerX, centerY - 10);

        ctx.fillStyle = '#F8FAFC';
        ctx.font = 'bold 16px sans-serif';
        ctx.fillText(`${currency} ${formatNumber(totalExpense)}`, centerX, centerY + 14);
    }

    // HAND-DRAWN MONTHLY BAR CHART
    function drawMonthlyBarChart(data) {
        const canvas = document.getElementById('monthlyCanvas');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        const padding = { top: 30, right: 30, bottom: 40, left: 60 };
        const chartWidth = canvas.width - padding.left - padding.right;
        const chartHeight = canvas.height - padding.top - padding.bottom;

        // Determine max value
        let maxVal = 1000;
        data.forEach(m => {
            maxVal = Math.max(maxVal, Number(m.income), Number(m.expense));
        });
        maxVal = Math.ceil(maxVal * 1.15); // headroom

        // Draw horizontal grid lines
        const gridSteps = 4;
        ctx.font = '11px sans-serif';
        ctx.fillStyle = '#64748B';
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';

        for (let i = 0; i <= gridSteps; i++) {
            const y = padding.top + (chartHeight / gridSteps) * i;
            const val = maxVal - (maxVal / gridSteps) * i;

            ctx.beginPath();
            ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
            ctx.moveTo(padding.left, y);
            ctx.lineTo(canvas.width - padding.right, y);
            ctx.stroke();

            ctx.fillText(formatNumber(val).split('.')[0], padding.left - 10, y);
        }

        const barGroupWidth = chartWidth / data.length;
        const singleBarWidth = Math.max(4, (barGroupWidth - 14) / 2);
        const monthsAbbr = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

        data.forEach((item, index) => {
            const xGroup = padding.left + index * barGroupWidth + 7;

            // Income bar (green)
            const incomeHeight = (Number(item.income) / maxVal) * chartHeight;
            const incomeY = padding.top + chartHeight - incomeHeight;
            ctx.fillStyle = '#10B981';
            ctx.beginPath();
            ctx.roundRect ? ctx.roundRect(xGroup, incomeY, singleBarWidth, incomeHeight, [3, 3, 0, 0])
                           : ctx.rect(xGroup, incomeY, singleBarWidth, incomeHeight);
            ctx.fill();

            // Expense bar (red)
            const expenseHeight = (Number(item.expense) / maxVal) * chartHeight;
            const expenseY = padding.top + chartHeight - expenseHeight;
            ctx.fillStyle = '#EF4444';
            ctx.beginPath();
            ctx.roundRect ? ctx.roundRect(xGroup + singleBarWidth + 2, expenseY, singleBarWidth, expenseHeight, [3, 3, 0, 0])
                           : ctx.rect(xGroup + singleBarWidth + 2, expenseY, singleBarWidth, expenseHeight);
            ctx.fill();

            // X-axis month label
            ctx.fillStyle = '#94A3B8';
            ctx.textAlign = 'center';
            ctx.fillText(monthsAbbr[index], xGroup + singleBarWidth, canvas.height - padding.bottom + 18);
        });
    }

    // DASHBOARD BUDGETS
    function renderDashboardBudgets(budgets, currency) {
        const container = document.getElementById('dash-budget-progress-container');
        container.innerHTML = '';

        if (!budgets || budgets.length === 0) {
            container.innerHTML = '<div class="empty-state">No budgets set for this month. Go to the Budgets tab to set one!</div>';
            return;
        }

        budgets.forEach(b => {
            const statusClass = b.status === 'EXCEEDED' ? 'exceeded' : (b.status === 'WARNING' ? 'warning' : 'ok');
            const percentWidth = Math.min(100, Math.max(0, Number(b.percentUsed)));

            const item = document.createElement('div');
            item.className = 'budget-progress-item';
            item.innerHTML = `
                <div class="bp-header">
                    <span class="bp-title">${escapeHtml(b.categoryName)}</span>
                    <span class="bp-meta">${currency} ${formatNumber(b.spentAmount)} of ${currency} ${formatNumber(b.limitAmount)} (${b.percentUsed.toFixed(1)}%)</span>
                </div>
                <div class="bp-track">
                    <div class="bp-fill ${statusClass}" style="width: ${percentWidth}%"></div>
                </div>
            `;
            container.appendChild(item);
        });
    }

    // TRANSACTIONS VIEW
    async function loadTransactions() {
        const search = document.getElementById('filter-search').value;
        const type = document.getElementById('filter-type').value;
        const categoryId = document.getElementById('filter-category').value;
        const paymentMethod = document.getElementById('filter-payment').value;
        const from = document.getElementById('filter-from').value;
        const to = document.getElementById('filter-to').value;
        const minAmount = document.getElementById('filter-min').value;
        const maxAmount = document.getElementById('filter-max').value;

        const params = new URLSearchParams();
        params.append('page', state.currentPage);
        params.append('size', state.pageSize);
        params.append('sort', 'transactionDate,desc');

        if (search) params.append('search', search);
        if (type) params.append('type', type);
        if (categoryId) params.append('categoryId', categoryId);
        if (paymentMethod) params.append('paymentMethod', paymentMethod);
        if (from) params.append('from', from);
        if (to) params.append('to', to);
        if (minAmount) params.append('minAmount', minAmount);
        if (maxAmount) params.append('maxAmount', maxAmount);

        try {
            const data = await apiRequest(`/api/transactions?${params.toString()}`);
            state.transactions = data.content;
            state.totalPages = data.totalPages;
            state.totalElements = data.totalElements;

            renderTransactionsTable();
            updatePaginationUI();
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    function renderTransactionsTable() {
        const tbody = document.getElementById('transactions-tbody');
        const emptyState = document.getElementById('transactions-empty');
        tbody.innerHTML = '';

        if (!state.transactions || state.transactions.length === 0) {
            emptyState.classList.remove('hidden');
            return;
        }
        emptyState.classList.add('hidden');

        const currency = (state.user && state.user.currency) || '₹';

        state.transactions.forEach(t => {
            const tr = document.createElement('tr');
            const isExpense = t.type === 'EXPENSE';
            const amountPrefix = isExpense ? '-' : '+';
            const amountClass = isExpense ? 'amount-expense' : 'amount-income';
            const badgeClass = isExpense ? 'badge-expense' : 'badge-income';

            tr.innerHTML = `
                <td>${escapeHtml(t.transactionDate)}</td>
                <td>
                    <div style="font-weight: 500;">${escapeHtml(t.description)}</div>
                    ${t.notes ? `<div style="font-size:0.75rem; color:var(--text-muted);">${escapeHtml(t.notes)}</div>` : ''}
                </td>
                <td>
                    <span class="badge" style="background:${escapeHtml(t.categoryColor)}22; color:${escapeHtml(t.categoryColor)};">
                        ${escapeHtml(t.categoryName)}
                    </span>
                </td>
                <td>${escapeHtml(t.paymentMethod)}</td>
                <td><span class="badge ${badgeClass}">${escapeHtml(t.type)}</span></td>
                <td class="text-right ${amountClass}">${amountPrefix}${currency} ${formatNumber(t.amount)}</td>
                <td class="text-center">
                    <button type="button" class="action-btn edit" data-id="${t.id}" title="Edit Transaction">✏️</button>
                    <button type="button" class="action-btn delete" data-id="${t.id}" title="Delete Transaction">🗑️</button>
                </td>
            `;

            tr.querySelector('.edit').addEventListener('click', () => openEditTransactionModal(t));
            tr.querySelector('.delete').addEventListener('click', () => deleteTransaction(t.id));

            tbody.appendChild(tr);
        });
    }

    function updatePaginationUI() {
        const info = document.getElementById('pagination-info');
        const prevBtn = document.getElementById('prev-page-btn');
        const nextBtn = document.getElementById('next-page-btn');
        const pageNum = document.getElementById('page-num-display');

        const start = state.currentPage * state.pageSize + (state.totalElements > 0 ? 1 : 0);
        const end = Math.min((state.currentPage + 1) * state.pageSize, state.totalElements);

        info.textContent = `Showing ${start}-${end} of ${state.totalElements}`;
        pageNum.textContent = `Page ${state.currentPage + 1} of ${Math.max(1, state.totalPages)}`;

        prevBtn.disabled = state.currentPage === 0;
        nextBtn.disabled = state.currentPage >= state.totalPages - 1;
    }

    // TRANSACTION MODAL (ADD / EDIT)
    function openAddTransactionModal() {
        document.getElementById('tx-modal-title').textContent = 'Add Transaction';
        document.getElementById('tx-id').value = '';
        document.getElementById('tx-type').value = 'EXPENSE';
        populateCategoryDropdowns();
        document.getElementById('tx-amount').value = '';
        document.getElementById('tx-date').value = new Date().toISOString().split('T')[0];
        document.getElementById('tx-payment').value = 'UPI';
        document.getElementById('tx-description').value = '';
        document.getElementById('tx-notes').value = '';

        document.getElementById('transaction-modal').classList.remove('hidden');
    }

    function openEditTransactionModal(t) {
        document.getElementById('tx-modal-title').textContent = 'Edit Transaction';
        document.getElementById('tx-id').value = t.id;
        document.getElementById('tx-type').value = t.type;
        populateCategoryDropdowns();
        document.getElementById('tx-category').value = t.categoryId;
        document.getElementById('tx-amount').value = t.amount;
        document.getElementById('tx-date').value = t.transactionDate;
        document.getElementById('tx-payment').value = t.paymentMethod;
        document.getElementById('tx-description').value = t.description;
        document.getElementById('tx-notes').value = t.notes || '';

        document.getElementById('transaction-modal').classList.remove('hidden');
    }

    async function handleTransactionSubmit(e) {
        e.preventDefault();
        const id = document.getElementById('tx-id').value;
        const payload = {
            categoryId: Number(document.getElementById('tx-category').value),
            type: document.getElementById('tx-type').value,
            amount: Number(document.getElementById('tx-amount').value),
            description: document.getElementById('tx-description').value.trim(),
            transactionDate: document.getElementById('tx-date').value,
            paymentMethod: document.getElementById('tx-payment').value,
            notes: document.getElementById('tx-notes').value.trim() || null
        };

        try {
            let res;
            if (id) {
                res = await apiRequest(`/api/transactions/${id}`, {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
                showToast('Transaction updated successfully!', 'success');
            } else {
                res = await apiRequest('/api/transactions', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
                showToast('Transaction created successfully!', 'success');
            }

            // Display budget warning if present
            if (res && res.budgetWarning) {
                showToast(`⚠️ ${res.budgetWarning}`, 'warning');
            }

            document.getElementById('transaction-modal').classList.add('hidden');
            loadTransactions();
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    async function deleteTransaction(id) {
        if (!confirm('Are you sure you want to delete this transaction?')) return;
        try {
            await apiRequest(`/api/transactions/${id}`, { method: 'DELETE' });
            showToast('Transaction deleted', 'success');
            loadTransactions();
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    // CSV DOWNLOAD
    async function downloadCsv() {
        const from = document.getElementById('filter-from').value;
        const to = document.getElementById('filter-to').value;
        let query = '';
        if (from && to) query = `?from=${from}&to=${to}`;

        try {
            const blob = await apiRequest(`/api/reports/export${query}`);
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `transactions_${new Date().toISOString().split('T')[0]}.csv`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            showToast('CSV downloaded successfully', 'success');
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    // CATEGORIES VIEW
    function renderCategoriesView() {
        const container = document.getElementById('categories-container');
        container.innerHTML = '';

        state.categories.forEach(c => {
            const card = document.createElement('div');
            card.className = 'category-card';
            card.innerHTML = `
                <div class="cat-info">
                    <span class="cat-color-dot" style="background:${escapeHtml(c.color || '#3B82F6')}"></span>
                    <div>
                        <div class="cat-name">${escapeHtml(c.name)}</div>
                        <div class="cat-type">${escapeHtml(c.type)} • ${c.isGlobal ? 'Global' : 'Custom'}</div>
                    </div>
                </div>
                ${!c.isGlobal ? `
                    <div>
                        <button type="button" class="action-btn edit" data-id="${c.id}">✏️</button>
                        <button type="button" class="action-btn delete" data-id="${c.id}">🗑️</button>
                    </div>
                ` : '<span style="font-size:0.75rem; color:var(--text-muted);">Default</span>'}
            `;

            if (!c.isGlobal) {
                card.querySelector('.edit').addEventListener('click', () => openEditCategoryModal(c));
                card.querySelector('.delete').addEventListener('click', () => deleteCategory(c.id));
            }

            container.appendChild(card);
        });
    }

    function openAddCategoryModal() {
        document.getElementById('cat-modal-title').textContent = 'Add Category';
        document.getElementById('cat-id').value = '';
        document.getElementById('cat-name').value = '';
        document.getElementById('cat-type').value = 'EXPENSE';
        document.getElementById('cat-color').value = '#3B82F6';
        document.getElementById('cat-icon').value = 'tag';
        document.getElementById('category-modal').classList.remove('hidden');
    }

    function openEditCategoryModal(c) {
        document.getElementById('cat-modal-title').textContent = 'Edit Category';
        document.getElementById('cat-id').value = c.id;
        document.getElementById('cat-name').value = c.name;
        document.getElementById('cat-type').value = c.type;
        document.getElementById('cat-color').value = c.color || '#3B82F6';
        document.getElementById('cat-icon').value = c.icon || 'tag';
        document.getElementById('category-modal').classList.remove('hidden');
    }

    async function handleCategorySubmit(e) {
        e.preventDefault();
        const id = document.getElementById('cat-id').value;
        const payload = {
            name: document.getElementById('cat-name').value.trim(),
            type: document.getElementById('cat-type').value,
            color: document.getElementById('cat-color').value,
            icon: document.getElementById('cat-icon').value.trim()
        };

        try {
            if (id) {
                await apiRequest(`/api/categories/${id}`, {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
                showToast('Category updated', 'success');
            } else {
                await apiRequest('/api/categories', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
                showToast('Category created', 'success');
            }
            document.getElementById('category-modal').classList.add('hidden');
            await loadCategories();
            renderCategoriesView();
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    async function deleteCategory(id) {
        if (!confirm('Are you sure you want to delete this custom category?')) return;
        try {
            await apiRequest(`/api/categories/${id}`, { method: 'DELETE' });
            showToast('Category deleted', 'success');
            await loadCategories();
            renderCategoriesView();
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    // BUDGETS VIEW
    async function loadBudgetsView() {
        const picker = document.getElementById('budget-month-picker');
        if (!picker.value) {
            const now = new Date();
            picker.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
        }

        const month = picker.value;
        document.getElementById('budget-status-month-title').textContent = `Month: ${month}`;

        try {
            const statuses = await apiRequest(`/api/budgets/status?month=${month}`);
            renderBudgetsStatusGrid(statuses);
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    function renderBudgetsStatusGrid(statuses) {
        const container = document.getElementById('budgets-status-container');
        container.innerHTML = '';

        if (!statuses || statuses.length === 0) {
            container.innerHTML = '<div class="empty-state">No budgets configured for this month. Click "+ Set Budget" above!</div>';
            return;
        }

        const currency = (state.user && state.user.currency) || '₹';

        statuses.forEach(b => {
            const card = document.createElement('div');
            card.className = 'budget-card';

            const statusClass = b.status === 'EXCEEDED' ? 'exceeded' : (b.status === 'WARNING' ? 'warning' : 'ok');
            const percentWidth = Math.min(100, Math.max(0, Number(b.percentUsed)));

            card.innerHTML = `
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
                    <div style="font-weight:700; font-size:1.05rem;">${escapeHtml(b.categoryName)}</div>
                    <span class="status-tag ${b.status}">${escapeHtml(b.status)}</span>
                </div>
                <div style="display:flex; justify-content:space-between; margin-bottom:8px; font-size:0.88rem;">
                    <span style="color:var(--text-secondary);">Spent: ${currency} ${formatNumber(b.spentAmount)}</span>
                    <span style="font-weight:600;">Limit: ${currency} ${formatNumber(b.limitAmount)}</span>
                </div>
                <div class="bp-track" style="margin-bottom:12px;">
                    <div class="bp-fill ${statusClass}" style="width:${percentWidth}%"></div>
                </div>
                <div style="display:flex; justify-content:space-between; align-items:center;">
                    <span style="font-size:0.8rem; color:var(--text-muted);">${b.percentUsed.toFixed(1)}% utilized • Remaining: ${currency} ${formatNumber(b.remainingAmount)}</span>
                    <div>
                        <button type="button" class="action-btn delete" data-id="${b.id}" title="Delete Budget">🗑️</button>
                    </div>
                </div>
            `;

            card.querySelector('.delete').addEventListener('click', () => deleteBudget(b.id));

            container.appendChild(card);
        });
    }

    function openAddBudgetModal() {
        document.getElementById('budget-modal-title').textContent = 'Set Monthly Budget';
        document.getElementById('budget-id').value = '';
        const now = new Date();
        document.getElementById('budget-month').value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
        populateCategoryDropdowns();
        document.getElementById('budget-category').value = '';
        document.getElementById('budget-limit').value = '';
        document.getElementById('budget-modal').classList.remove('hidden');
    }

    async function handleBudgetSubmit(e) {
        e.preventDefault();
        const id = document.getElementById('budget-id').value;
        const catVal = document.getElementById('budget-category').value;
        const payload = {
            categoryId: catVal ? Number(catVal) : null,
            month: document.getElementById('budget-month').value,
            limitAmount: Number(document.getElementById('budget-limit').value)
        };

        try {
            if (id) {
                await apiRequest(`/api/budgets/${id}`, {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
                showToast('Budget updated', 'success');
            } else {
                await apiRequest('/api/budgets', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
                showToast('Budget configured successfully', 'success');
            }
            document.getElementById('budget-modal').classList.add('hidden');
            loadBudgetsView();
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    async function deleteBudget(id) {
        if (!confirm('Are you sure you want to delete this budget?')) return;
        try {
            await apiRequest(`/api/budgets/${id}`, { method: 'DELETE' });
            showToast('Budget removed', 'success');
            loadBudgetsView();
        } catch (err) {
            showToast(err.message, 'danger');
        }
    }

    // EVENT LISTENERS & SETUP
    function setupEventListeners() {
        // Auth form switches
        document.getElementById('switch-to-register-btn').addEventListener('click', () => {
            document.getElementById('login-form').classList.add('hidden');
            document.getElementById('register-form').classList.remove('hidden');
            document.getElementById('auth-heading').textContent = 'Create an Account';
            document.getElementById('auth-subheading').textContent = 'Start tracking your expenses in seconds.';
        });

        document.getElementById('switch-to-login-btn').addEventListener('click', () => {
            document.getElementById('register-form').classList.add('hidden');
            document.getElementById('login-form').classList.remove('hidden');
            document.getElementById('auth-heading').textContent = 'Welcome Back';
            document.getElementById('auth-subheading').textContent = 'Track your expenses, manage budgets, and save smarter.';
        });

        // Demo fill button
        document.getElementById('demo-fill-btn').addEventListener('click', () => {
            document.getElementById('login-email').value = 'demo@expensetracker.com';
            document.getElementById('login-password').value = 'Password123';
        });

        // Login submit
        document.getElementById('login-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const email = document.getElementById('login-email').value.trim();
            const password = document.getElementById('login-password').value;

            try {
                const res = await apiRequest('/api/auth/login', {
                    method: 'POST',
                    body: JSON.stringify({ email, password })
                });
                showToast('Login successful!', 'success');
                setSession(res.token, { id: res.userId, name: res.name, email: res.email, currency: res.currency });
            } catch (err) {
                showToast(err.message, 'danger');
            }
        });

        // Register submit
        document.getElementById('register-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const name = document.getElementById('reg-name').value.trim();
            const email = document.getElementById('reg-email').value.trim();
            const password = document.getElementById('reg-password').value;
            const currency = document.getElementById('reg-currency').value;

            try {
                const res = await apiRequest('/api/auth/register', {
                    method: 'POST',
                    body: JSON.stringify({ name, email, password, currency })
                });
                showToast('Account registered successfully!', 'success');
                setSession(res.token, { id: res.userId, name: res.name, email: res.email, currency: res.currency });
            } catch (err) {
                showToast(err.message, 'danger');
            }
        });

        // Logout
        document.getElementById('logout-btn').addEventListener('click', logout);

        const demoBtn = document.getElementById('demo-fill-btn');
        if (demoBtn) {
            demoBtn.addEventListener('click', () => {
                document.getElementById('login-email').value = 'demo@expensetracker.com';
                document.getElementById('login-password').value = 'Password123';
            });
        }

        // Tab buttons
        document.querySelectorAll('.nav-tab').forEach(tab => {
            tab.addEventListener('click', () => switchTab(tab.dataset.tab));
        });

        // Dashboard filters
        document.getElementById('dash-apply-btn').addEventListener('click', loadDashboard);
        document.getElementById('dash-reset-btn').addEventListener('click', () => {
            document.getElementById('dash-from').value = '';
            document.getElementById('dash-to').value = '';
            loadDashboard();
        });

        // Transactions filters
        document.getElementById('apply-filters-btn').addEventListener('click', () => {
            state.currentPage = 0;
            loadTransactions();
        });
        document.getElementById('reset-filters-btn').addEventListener('click', () => {
            document.getElementById('filter-search').value = '';
            document.getElementById('filter-type').value = '';
            document.getElementById('filter-category').value = '';
            document.getElementById('filter-payment').value = '';
            document.getElementById('filter-from').value = '';
            document.getElementById('filter-to').value = '';
            document.getElementById('filter-min').value = '';
            document.getElementById('filter-max').value = '';
            state.currentPage = 0;
            loadTransactions();
        });

        // CSV export
        document.getElementById('export-csv-btn').addEventListener('click', downloadCsv);

        // Pagination
        document.getElementById('prev-page-btn').addEventListener('click', () => {
            if (state.currentPage > 0) {
                state.currentPage--;
                loadTransactions();
            }
        });
        document.getElementById('next-page-btn').addEventListener('click', () => {
            if (state.currentPage < state.totalPages - 1) {
                state.currentPage++;
                loadTransactions();
            }
        });

        // Transaction Type change updates Category options in modal
        document.getElementById('tx-type').addEventListener('change', populateCategoryDropdowns);

        // Modals
        document.getElementById('add-transaction-modal-btn').addEventListener('click', openAddTransactionModal);
        document.getElementById('close-tx-modal').addEventListener('click', () => document.getElementById('transaction-modal').classList.add('hidden'));
        document.getElementById('cancel-tx-modal').addEventListener('click', () => document.getElementById('transaction-modal').classList.add('hidden'));
        document.getElementById('transaction-form').addEventListener('submit', handleTransactionSubmit);

        document.getElementById('add-category-modal-btn').addEventListener('click', openAddCategoryModal);
        document.getElementById('close-cat-modal').addEventListener('click', () => document.getElementById('category-modal').classList.add('hidden'));
        document.getElementById('cancel-cat-modal').addEventListener('click', () => document.getElementById('category-modal').classList.add('hidden'));
        document.getElementById('category-form').addEventListener('submit', handleCategorySubmit);

        document.getElementById('add-budget-modal-btn').addEventListener('click', openAddBudgetModal);
        document.getElementById('close-budget-modal').addEventListener('click', () => document.getElementById('budget-modal').classList.add('hidden'));
        document.getElementById('cancel-budget-modal').addEventListener('click', () => document.getElementById('budget-modal').classList.add('hidden'));
        document.getElementById('budget-form').addEventListener('submit', handleBudgetSubmit);

        document.getElementById('budget-month-picker').addEventListener('change', loadBudgetsView);
    }

    // Startup
    document.addEventListener('DOMContentLoaded', () => {
        setupEventListeners();
        updateAuthUI();
    });
})();
