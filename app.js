async function api(url, options = {}) {
    const res = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...options });
    if (!res.ok) throw new Error((await res.json()).error || 'Request failed');
    return res.json().catch(() => ({}));
}

async function login() {
    try {
        await api('/api/login', { method: 'POST', body: JSON.stringify({ username: document.getElementById('username').value, password: document.getElementById('password').value }) });
        document.getElementById('loginPage').classList.add('hidden');
        document.getElementById('appView').classList.remove('hidden');
        loadOverview();
    } catch (e) { alert(e.message); }
}

async function logout() {
    await api('/api/logout', { method: 'POST' });
    location.reload();
}

function showTab(tab) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.add('hidden'));
    document.getElementById(tab + 'Tab').classList.remove('hidden');
    if (tab === 'overview') loadOverview();
    if (tab === 'students') loadStudents();
    if (tab === 'stats') loadStats();
}

async function loadOverview() {
    const data = await api('/api/dashboard');
    document.getElementById('totalStudents').innerText = data.totalStudents;
    document.getElementById('avgPred').innerText = data.averagePrediction;
    document.getElementById('avgAtt').innerText = data.averageAttendance + '%';
    document.getElementById('atRisk').innerText = data.atRisk;
}

async function loadStudents() {
    const students = await api('/api/students');
    const tbody = document.getElementById('studentTableBody');
    tbody.innerHTML = students.map(s => `
        <tr>
            <td>${s.name}</td>
            <td>${s.rollNo}</td>
            <td>${s.department}</td>
            <td>${s.attendance}%</td>
            <td>${s.internalMarks}</td>
            <td>${s.studyHours}h</td>
            <td>${s.predictedScore}</td>
            <td>${s.risk}</td>
            <td><button onclick="deleteStudent('${s.id}')">Delete</button></td>
        </tr>
    `).join('');
}

async function deleteStudent(id) {
    if (confirm('Delete this record?')) {
        await api('/api/students/' + id, { method: 'DELETE' });
        loadStudents();
    }
}

async function loadStats() {
    const stats = await api('/api/stats');
    document.getElementById('statsText').innerText = JSON.stringify(stats, null, 2);
}