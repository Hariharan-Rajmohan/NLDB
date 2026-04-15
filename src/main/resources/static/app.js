/* ============================================
   NLDB — Frontend Logic (with Voice Features)
   ============================================ */

(function () {
    'use strict';

    // =============================================
    // WELCOME PAGE — Login & Persistence
    // =============================================
    const welcomePage    = document.getElementById('welcome-page');
    const appContainer   = document.getElementById('app-container');
    const googleLoginBtn = document.getElementById('google-login-btn');
    const githubLoginBtn = document.getElementById('github-login-btn');
    const guestLoginBtn  = document.getElementById('guest-login-btn');
    const signinForm     = document.getElementById('signin-form');
    const signupToggle   = document.getElementById('signup-toggle-link');
    const signinSubmitBtn = document.getElementById('signin-submit-btn');

    // Check if already logged in (persistent session)
    const savedSession = localStorage.getItem('nldb_session');
    if (savedSession) {
        // Skip welcome page, go straight to app
        welcomePage.style.display = 'none';
        appContainer.classList.remove('hidden');
        appContainer.classList.add('app-visible');
    }

    // --- Transition to app ---
    function loginAndShowApp(method, userEmail) {
        const session = {
            method: method,
            email: userEmail || 'guest',
            loginTime: new Date().toISOString()
        };

        // Save to localStorage if "Remember me" is checked or it's a social/guest login
        const rememberMe = document.getElementById('remember-me');
        if (!rememberMe || rememberMe.checked || method !== 'email') {
            localStorage.setItem('nldb_session', JSON.stringify(session));
        }

        // Animate welcome page out
        welcomePage.classList.add('fade-out');
        setTimeout(() => {
            welcomePage.style.display = 'none';
            appContainer.classList.remove('hidden');
            appContainer.classList.add('app-visible');
        }, 600);
    }

    // 1. Email/Password form submit
    signinForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const email = document.getElementById('signin-email').value.trim();
        loginAndShowApp('email', email);
    });

    // 2. Google login
    googleLoginBtn.addEventListener('click', () => {
        loginAndShowApp('google', 'user@gmail.com');
    });

    // 3. GitHub login
    githubLoginBtn.addEventListener('click', () => {
        loginAndShowApp('github', 'user@github.com');
    });

    // 4. Guest login
    guestLoginBtn.addEventListener('click', () => {
        loginAndShowApp('guest', 'guest');
    });

    // Sign Up / Sign In toggle
    let isSignUp = false;
    signupToggle.addEventListener('click', (e) => {
        e.preventDefault();
        isSignUp = !isSignUp;
        const btnText = signinSubmitBtn.querySelector('.signin-btn-text');
        if (isSignUp) {
            btnText.textContent = 'Sign Up';
            signupToggle.textContent = 'Sign in';
            signupToggle.parentElement.childNodes[0].textContent = 'Already have an account? ';
        } else {
            btnText.textContent = 'Sign In';
            signupToggle.textContent = 'Sign up';
            signupToggle.parentElement.childNodes[0].textContent = "Don't have an account? ";
        }
    });

    // --- DOM References ---
    const queryInput   = document.getElementById('query-input');
    const submitBtn    = document.getElementById('submit-btn');
    const sqlSection   = document.getElementById('sql-section');
    const sqlDisplay   = document.getElementById('sql-display');
    const copyBtn      = document.getElementById('copy-btn');
    const errorSection = document.getElementById('error-section');
    const errorMessage = document.getElementById('error-message');
    const resultsSection = document.getElementById('results-section');
    const tableHead    = document.getElementById('table-head');
    const tableBody    = document.getElementById('table-body');
    const rowCount     = document.getElementById('row-count');
    const chips        = document.querySelectorAll('.chip');

    // Voice DOM References
    const micBtn              = document.getElementById('mic-btn');
    const speakBtn            = document.getElementById('speak-btn');
    const listeningOverlay    = document.getElementById('listening-overlay');
    const listeningTranscript = document.getElementById('listening-transcript');
    const stopListeningBtn    = document.getElementById('stop-listening-btn');

    const API_URL = '/query';

    // --- Voice State ---
    let recognition = null;
    let isListening = false;
    let isSpeaking  = false;
    let lastResults  = null; // store last query results for TTS

    // --- Utility: HTML escape to prevent XSS ---
    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        const text = String(str);
        const div = document.createElement('div');
        div.appendChild(document.createTextNode(text));
        return div.innerHTML;
    }

    // --- UI State Helpers ---
    function setLoading(isLoading) {
        submitBtn.disabled = isLoading;
        if (isLoading) {
            submitBtn.classList.add('loading');
        } else {
            submitBtn.classList.remove('loading');
        }
    }

    function hideAll() {
        sqlSection.classList.add('hidden');
        errorSection.classList.add('hidden');
        resultsSection.classList.add('hidden');
    }

    function showError(msg) {
        errorMessage.textContent = msg;
        errorSection.classList.remove('hidden');
        errorSection.style.animation = 'none';
        errorSection.offsetHeight; // trigger reflow
        errorSection.style.animation = '';
    }

    function showSql(sql) {
        sqlDisplay.textContent = sql;
        sqlSection.classList.remove('hidden');
        sqlSection.style.animation = 'none';
        sqlSection.offsetHeight;
        sqlSection.style.animation = '';
    }

    function showResults(data) {
        lastResults = data; // store for TTS

        if (!data || data.length === 0) {
            rowCount.textContent = '0 rows';
            tableHead.innerHTML = '';
            tableBody.innerHTML = '<tr><td style="text-align:center;color:var(--text-muted);padding:24px;">No results found</td></tr>';
            resultsSection.classList.remove('hidden');
            return;
        }

        // Build table header
        const columns = Object.keys(data[0]);
        tableHead.innerHTML = '<tr>' + columns.map(col =>
            '<th>' + escapeHtml(col) + '</th>'
        ).join('') + '</tr>';

        // Build table body with staggered animation
        tableBody.innerHTML = data.map((row, idx) =>
            '<tr style="animation: fadeInUp 0.3s ' + (idx * 0.04) + 's both">' +
            columns.map(col =>
                '<td>' + escapeHtml(row[col]) + '</td>'
            ).join('') + '</tr>'
        ).join('');

        rowCount.textContent = data.length + (data.length === 1 ? ' row' : ' rows');
        resultsSection.classList.remove('hidden');
        resultsSection.style.animation = 'none';
        resultsSection.offsetHeight;
        resultsSection.style.animation = '';
    }

    // --- Main Query Handler ---
    async function runQuery(queryText) {
        const query = queryText.trim();
        if (!query) {
            queryInput.focus();
            return;
        }

        hideAll();
        setLoading(true);
        stopSpeaking(); // stop any ongoing TTS

        try {
            const response = await fetch(API_URL, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userQuery: query })
            });

            if (!response.ok) {
                throw new Error('Server error (HTTP ' + response.status + '). Please try again.');
            }

            const data = await response.json();

            // Show SQL if available
            if (data.sql) {
                showSql(data.sql);
            }

            // Check for errors
            if (data.error) {
                showError(data.error);
                return;
            }

            // Show results
            showResults(data.results);

        } catch (err) {
            showError(err.message || 'An unexpected error occurred. Please check if the server is running.');
        } finally {
            setLoading(false);
        }
    }

    // =============================================
    // VOICE INPUT — Speech Recognition
    // =============================================

    function initSpeechRecognition() {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognition) {
            micBtn.title = 'Voice input not supported in this browser';
            micBtn.disabled = true;
            micBtn.style.opacity = '0.4';
            return;
        }

        recognition = new SpeechRecognition();
        recognition.continuous = false;
        recognition.interimResults = true;
        recognition.lang = 'en-US';
        recognition.maxAlternatives = 1;

        recognition.onstart = function () {
            isListening = true;
            micBtn.classList.add('listening');
            listeningOverlay.classList.remove('hidden');
            listeningTranscript.textContent = '';
            micBtn.querySelector('.mic-icon').style.display = 'none';
            micBtn.querySelector('.mic-stop-icon').style.display = 'block';
        };

        recognition.onresult = function (event) {
            let interim = '';
            let final = '';
            for (let i = event.resultIndex; i < event.results.length; i++) {
                const transcript = event.results[i][0].transcript;
                if (event.results[i].isFinal) {
                    final += transcript;
                } else {
                    interim += transcript;
                }
            }
            // Show live transcript in overlay
            listeningTranscript.textContent = final || interim;
            if (final) {
                queryInput.value = final;
            }
        };

        recognition.onend = function () {
            isListening = false;
            micBtn.classList.remove('listening');
            listeningOverlay.classList.add('hidden');
            micBtn.querySelector('.mic-icon').style.display = 'block';
            micBtn.querySelector('.mic-stop-icon').style.display = 'none';

            // Auto-submit if we got text
            const text = queryInput.value.trim();
            if (text) {
                runQuery(text);
            }
        };

        recognition.onerror = function (event) {
            isListening = false;
            micBtn.classList.remove('listening');
            listeningOverlay.classList.add('hidden');
            micBtn.querySelector('.mic-icon').style.display = 'block';
            micBtn.querySelector('.mic-stop-icon').style.display = 'none';

            if (event.error === 'not-allowed') {
                showError('Microphone access denied. Please allow microphone permission in your browser settings.');
            } else if (event.error !== 'aborted' && event.error !== 'no-speech') {
                showError('Voice recognition error: ' + event.error);
            }
        };
    }

    function startListening() {
        if (!recognition) return;
        if (isListening) {
            recognition.abort();
            return;
        }
        try {
            recognition.start();
        } catch (e) {
            // Already started
        }
    }

    function stopListening() {
        if (recognition && isListening) {
            recognition.stop();
        }
    }

    // =============================================
    // TEXT-TO-SPEECH — Speech Synthesis
    // =============================================

    function buildResultsSummary(data) {
        if (!data || data.length === 0) {
            return 'No results found.';
        }

        const columns = Object.keys(data[0]);
        const total = data.length;
        let summary = 'Found ' + total + (total === 1 ? ' row' : ' rows') + '. ';

        // Read up to 5 rows
        const limit = Math.min(data.length, 5);
        for (let i = 0; i < limit; i++) {
            summary += 'Row ' + (i + 1) + ': ';
            const parts = columns.map(col => {
                const val = data[i][col];
                return col.replace(/_/g, ' ') + ' is ' + (val !== null && val !== undefined ? val : 'empty');
            });
            summary += parts.join(', ') + '. ';
        }

        if (data.length > 5) {
            summary += 'And ' + (data.length - 5) + ' more rows.';
        }

        return summary;
    }

    function speakResults() {
        if (!('speechSynthesis' in window)) {
            showError('Text-to-speech is not supported in this browser.');
            return;
        }

        if (isSpeaking) {
            stopSpeaking();
            return;
        }

        const text = buildResultsSummary(lastResults);
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.rate = 0.95;
        utterance.pitch = 1;
        utterance.volume = 1;

        // Try to pick a good English voice
        const voices = speechSynthesis.getVoices();
        const preferred = voices.find(v => v.lang.startsWith('en') && v.name.includes('Google'));
        if (preferred) {
            utterance.voice = preferred;
        } else {
            const english = voices.find(v => v.lang.startsWith('en'));
            if (english) utterance.voice = english;
        }

        utterance.onstart = function () {
            isSpeaking = true;
            speakBtn.classList.add('speaking');
            speakBtn.querySelector('.speak-icon').style.display = 'none';
            speakBtn.querySelector('.speak-stop-icon').style.display = 'block';
            speakBtn.querySelector('.speak-label').textContent = 'Stop';
        };

        utterance.onend = function () {
            resetSpeakingState();
        };

        utterance.onerror = function () {
            resetSpeakingState();
        };

        speechSynthesis.speak(utterance);
    }

    function stopSpeaking() {
        if ('speechSynthesis' in window) {
            speechSynthesis.cancel();
        }
        resetSpeakingState();
    }

    function resetSpeakingState() {
        isSpeaking = false;
        if (speakBtn) {
            speakBtn.classList.remove('speaking');
            speakBtn.querySelector('.speak-icon').style.display = 'block';
            speakBtn.querySelector('.speak-stop-icon').style.display = 'none';
            speakBtn.querySelector('.speak-label').textContent = 'Read Aloud';
        }
    }

    // =============================================
    // EVENT LISTENERS
    // =============================================

    // Submit button
    submitBtn.addEventListener('click', () => runQuery(queryInput.value));

    // Enter key
    queryInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            runQuery(queryInput.value);
        }
    });

    // Example chips
    chips.forEach(chip => {
        chip.addEventListener('click', () => {
            const query = chip.getAttribute('data-query');
            queryInput.value = query;
            runQuery(query);
        });
    });

    // Copy SQL button
    copyBtn.addEventListener('click', () => {
        const sql = sqlDisplay.textContent;
        if (!sql) return;

        navigator.clipboard.writeText(sql).then(() => {
            const label = copyBtn.querySelector('.copy-label');
            const originalText = label.textContent;
            copyBtn.classList.add('copied');
            label.textContent = 'Copied!';
            setTimeout(() => {
                copyBtn.classList.remove('copied');
                label.textContent = originalText;
            }, 2000);
        }).catch(() => {
            // Fallback for older browsers
            const textarea = document.createElement('textarea');
            textarea.value = sql;
            textarea.style.position = 'fixed';
            textarea.style.opacity = '0';
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            document.body.removeChild(textarea);
        });
    });

    // Mic button — voice input
    micBtn.addEventListener('click', startListening);

    // Overlay stop button
    stopListeningBtn.addEventListener('click', stopListening);

    // Speak button — read results aloud
    speakBtn.addEventListener('click', speakResults);

    // Preload voices (some browsers load them lazily)
    if ('speechSynthesis' in window) {
        speechSynthesis.getVoices();
        speechSynthesis.onvoiceschanged = () => speechSynthesis.getVoices();
    }

    // Initialize speech recognition
    initSpeechRecognition();

    // Auto-focus input on load
    queryInput.focus();

    // =============================================
    // NAVIGATION — View Switching
    // =============================================

    const navTabs    = document.querySelectorAll('.nav-tab');
    const queryView  = document.getElementById('query-view');
    const crudView   = document.getElementById('crud-view');
    const chatView   = document.getElementById('chat-view');

    navTabs.forEach(tab => {
        tab.addEventListener('click', () => {
            navTabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');

            const view = tab.getAttribute('data-view');
            queryView.classList.add('hidden');
            crudView.classList.add('hidden');
            chatView.classList.add('hidden');

            if (view === 'query-view') {
                queryView.classList.remove('hidden');
            } else if (view === 'crud-view') {
                crudView.classList.remove('hidden');
                loadCrudTables(); // load dynamic table list
            } else if (view === 'chat-view') {
                chatView.classList.remove('hidden');
                document.getElementById('chat-input').focus();
            }
        });
    });

    // =============================================
    // CRUD — Data Management
    // =============================================

    const crudTableTabsContainer = document.getElementById('crud-table-tabs');
    const crudTableHead = document.getElementById('crud-table-head');
    const crudTableBody = document.getElementById('crud-table-body');
    const crudRowCount  = document.getElementById('crud-row-count');
    const crudTableTitle = document.getElementById('crud-table-title');
    const crudAddBtn    = document.getElementById('crud-add-btn');
    const crudRefreshBtn = document.getElementById('crud-refresh-btn');
    const crudStatus    = document.getElementById('crud-status');
    const crudModal     = document.getElementById('crud-modal');
    const modalTitle    = document.getElementById('modal-title');
    const crudForm      = document.getElementById('crud-form');
    const crudFormFields = document.getElementById('crud-form-fields');
    const modalCloseBtn = document.getElementById('modal-close-btn');
    const modalCancelBtn = document.getElementById('modal-cancel-btn');

    let currentTable   = 'students';
    let editingId      = null;
    let tableColumns   = [];

    // Table column definitions (for known tables — dynamic tables use metadata)
    const TABLE_FIELDS = {
        students: [
            { name: 'name', label: 'Name', type: 'text', required: true },
            { name: 'department', label: 'Department', type: 'text', required: true },
            { name: 'enrollment_year', label: 'Enrollment Year', type: 'number', required: true }
        ],
        employees: [
            { name: 'name', label: 'Name', type: 'text', required: true },
            { name: 'department', label: 'Department', type: 'text', required: true },
            { name: 'hire_date', label: 'Hire Date', type: 'date', required: true }
        ]
    };

    /**
     * Dynamically loads the list of tables and creates tab buttons.
     */
    async function loadCrudTables() {
        try {
            const response = await fetch('/api/chat/tables');
            if (!response.ok) throw new Error('Failed to load tables');
            const tables = await response.json();

            crudTableTabsContainer.innerHTML = '';
            tables.forEach((tableName, idx) => {
                const btn = document.createElement('button');
                btn.className = 'crud-tab' + (tableName.toLowerCase() === currentTable ? ' active' : '');
                btn.setAttribute('data-table', tableName.toLowerCase());
                btn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                    '<rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/>' +
                    '</svg> ' + tableName.charAt(0) + tableName.slice(1).toLowerCase();
                btn.addEventListener('click', () => {
                    document.querySelectorAll('.crud-tab').forEach(t => t.classList.remove('active'));
                    btn.classList.add('active');
                    currentTable = tableName.toLowerCase();
                    crudTableTitle.textContent = tableName.charAt(0) + tableName.slice(1).toLowerCase();
                    loadCrudData();
                });
                crudTableTabsContainer.appendChild(btn);
            });

            // If current table exists in list, load its data
            if (tables.map(t => t.toLowerCase()).includes(currentTable)) {
                loadCrudData();
            } else if (tables.length > 0) {
                currentTable = tables[0].toLowerCase();
                crudTableTitle.textContent = tables[0].charAt(0) + tables[0].slice(1).toLowerCase();
                loadCrudData();
            }
        } catch (err) {
            showCrudStatus('Error loading tables: ' + err.message, 'error');
        }
    }

    // Table tab switching (for dynamically loaded tabs — handled in loadCrudTables)

    // Load data from API
    async function loadCrudData() {
        try {
            const response = await fetch('/api/crud/' + currentTable);
            if (!response.ok) throw new Error('Failed to load data');
            const data = await response.json();
            renderCrudTable(data.rows);
            crudRowCount.textContent = data.count + (data.count === 1 ? ' row' : ' rows');
        } catch (err) {
            showCrudStatus('Error loading data: ' + err.message, 'error');
        }
    }

    // Render CRUD table with edit/delete buttons
    function renderCrudTable(rows) {
        if (!rows || rows.length === 0) {
            crudTableHead.innerHTML = '';
            crudTableBody.innerHTML = '<tr><td style="text-align:center;color:var(--text-muted);padding:24px;" colspan="10">No records found</td></tr>';
            return;
        }

        const columns = Object.keys(rows[0]);
        crudTableHead.innerHTML = '<tr>' +
            columns.map(col => '<th>' + escapeHtml(col) + '</th>').join('') +
            '<th class="actions-col">Actions</th></tr>';

        crudTableBody.innerHTML = rows.map((row, idx) =>
            '<tr style="animation: fadeInUp 0.3s ' + (idx * 0.03) + 's both">' +
            columns.map(col => '<td>' + escapeHtml(row[col]) + '</td>').join('') +
            '<td class="actions-cell">' +
                '<button class="crud-row-btn edit-btn" onclick="window._crudEdit(' + row.ID + ')" title="Edit">' +
                    '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                        '<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>' +
                        '<path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>' +
                    '</svg>' +
                '</button>' +
                '<button class="crud-row-btn delete-btn" onclick="window._crudDelete(' + row.ID + ')" title="Delete">' +
                    '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                        '<polyline points="3 6 5 6 21 6"/>' +
                        '<path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>' +
                    '</svg>' +
                '</button>' +
            '</td></tr>'
        ).join('');
    }

    // Show CRUD status message
    function showCrudStatus(msg, type) {
        crudStatus.textContent = msg;
        crudStatus.className = 'crud-status ' + type;
        crudStatus.classList.remove('hidden');
        setTimeout(() => crudStatus.classList.add('hidden'), 4000);
    }

    // Open modal for add
    crudAddBtn.addEventListener('click', () => {
        editingId = null;
        modalTitle.textContent = 'Add ' + (currentTable === 'students' ? 'Student' : 'Employee');
        buildFormFields(null);
        crudModal.classList.remove('hidden');
    });

    // Refresh button
    crudRefreshBtn.addEventListener('click', loadCrudData);

    // Close modal
    function closeModal() {
        crudModal.classList.add('hidden');
        crudForm.reset();
    }
    modalCloseBtn.addEventListener('click', closeModal);
    modalCancelBtn.addEventListener('click', closeModal);
    crudModal.addEventListener('click', (e) => {
        if (e.target === crudModal) closeModal();
    });

    // Build form fields dynamically
    function buildFormFields(existingData) {
        // Try known fields first, fall back to dynamic column metadata
        const fields = TABLE_FIELDS[currentTable];
        if (fields) {
            crudFormFields.innerHTML = fields.map(f => {
                const value = existingData ? (existingData[f.name.toUpperCase()] || existingData[f.name] || '') : '';
                return '<div class="form-group">' +
                    '<label for="field-' + f.name + '">' + f.label + '</label>' +
                    '<input type="' + f.type + '" id="field-' + f.name + '" name="' + f.name + '" value="' + escapeHtml(value) + '"' +
                    (f.required ? ' required' : '') +
                    ' class="form-input" autocomplete="off">' +
                    '</div>';
            }).join('');
        } else {
            // Dynamic table — fetch column metadata
            fetch('/api/columns/' + currentTable)
                .then(r => r.json())
                .then(columns => {
                    crudFormFields.innerHTML = columns
                        .filter(c => c.COLUMN_NAME !== 'ID')
                        .map(c => {
                            const name = c.COLUMN_NAME.toLowerCase();
                            const label = name.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
                            const type = c.DATA_TYPE.includes('INT') ? 'number' :
                                         c.DATA_TYPE.includes('DATE') ? 'date' : 'text';
                            const value = existingData ? (existingData[c.COLUMN_NAME] || existingData[name] || '') : '';
                            return '<div class="form-group">' +
                                '<label for="field-' + name + '">' + label + '</label>' +
                                '<input type="' + type + '" id="field-' + name + '" name="' + name + '" value="' + escapeHtml(value) + '"' +
                                ' required class="form-input" autocomplete="off">' +
                                '</div>';
                        }).join('');
                })
                .catch(() => {
                    crudFormFields.innerHTML = '<p style="color:var(--error)">Could not load form fields.</p>';
                });
        }
    }

    // Form submit handler
    crudForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fields = TABLE_FIELDS[currentTable];
        const data = {};

        if (fields) {
            fields.forEach(f => {
                const input = document.getElementById('field-' + f.name);
                data[f.name] = input.value;
            });
        } else {
            // Dynamic table — collect all form inputs
            const inputs = crudFormFields.querySelectorAll('input');
            inputs.forEach(input => {
                data[input.name] = input.value;
            });
        }

        try {
            let url = '/api/crud/' + currentTable;
            let method = 'POST';
            if (editingId !== null) {
                url += '/' + editingId;
                method = 'PUT';
            }

            const response = await fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });

            const result = await response.json();
            if (!response.ok) {
                throw new Error(result.error || 'Operation failed');
            }

            closeModal();
            showCrudStatus(result.message || 'Operation successful!', 'success');
            loadCrudData();
        } catch (err) {
            showCrudStatus('Error: ' + err.message, 'error');
        }
    });

    // Global edit handler
    window._crudEdit = async function (id) {
        try {
            const response = await fetch('/api/crud/' + currentTable + '/' + id);
            if (!response.ok) throw new Error('Failed to load record');
            const data = await response.json();
            editingId = id;
            modalTitle.textContent = 'Edit ' + (currentTable === 'students' ? 'Student' : 'Employee') + ' #' + id;
            buildFormFields(data);
            crudModal.classList.remove('hidden');
        } catch (err) {
            showCrudStatus('Error loading record: ' + err.message, 'error');
        }
    };

    // Global delete handler
    window._crudDelete = async function (id) {
        if (!confirm('Are you sure you want to delete record #' + id + '?')) return;

        try {
            const response = await fetch('/api/crud/' + currentTable + '/' + id, { method: 'DELETE' });
            const result = await response.json();
            if (!response.ok) {
                throw new Error(result.error || 'Delete failed');
            }
            showCrudStatus(result.message || 'Record deleted!', 'success');
            loadCrudData();
        } catch (err) {
            showCrudStatus('Error: ' + err.message, 'error');
        }
    };

    // =============================================
    // CHAT — Dataset Creator
    // =============================================

    const chatMessages    = document.getElementById('chat-messages');
    const chatInput       = document.getElementById('chat-input');
    const chatSendBtn     = document.getElementById('chat-send-btn');
    const chatClearBtn    = document.getElementById('chat-clear-btn');
    const chatStarters    = document.getElementById('chat-starters');

    let chatHistory = []; // conversation history for context
    let chatLoading = false;

    // Send chat message
    async function sendChatMessage(text) {
        const message = text.trim();
        if (!message || chatLoading) return;

        // Hide starter prompts after first message
        chatStarters.classList.add('hidden');

        // Add user bubble
        appendChatBubble('user', message);
        chatInput.value = '';

        // Show typing indicator
        const typingEl = appendTypingIndicator();
        chatLoading = true;
        setChatLoading(true);

        try {
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    message: message,
                    history: chatHistory
                })
            });

            const data = await response.json();

            // Remove typing indicator
            typingEl.remove();

            if (data.error) {
                appendChatBubble('assistant', '❌ ' + data.error, data.sql);
            } else {
                appendChatBubble('assistant', data.reply || 'Done!', data.sql);

                // If tables were updated, refresh the CRUD tab list
                if (data.tablesUpdated) {
                    // Silently refresh table list in background
                    loadCrudTables();
                }
            }

            // Add to conversation history
            chatHistory.push({ role: 'user', content: message });
            chatHistory.push({ role: 'assistant', content: data.reply || data.error || '' });

            // Keep history to last 20 messages to avoid huge prompts
            if (chatHistory.length > 20) {
                chatHistory = chatHistory.slice(-20);
            }

        } catch (err) {
            typingEl.remove();
            appendChatBubble('assistant', '❌ Connection error. Make sure the server is running.');
        } finally {
            chatLoading = false;
            setChatLoading(false);
        }
    }

    // Append a chat bubble
    function appendChatBubble(role, text, sql) {
        const bubble = document.createElement('div');
        bubble.className = 'chat-bubble ' + (role === 'user' ? 'user-bubble' : 'assistant-bubble');

        let avatarHtml = '';
        if (role === 'assistant') {
            avatarHtml = '<div class="bubble-avatar">' +
                '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                '<path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>' +
                '</svg></div>';
        }

        let sqlHtml = '';
        if (sql) {
            sqlHtml = '<div class="bubble-sql"><code>' + escapeHtml(sql) + '</code></div>';
        }

        bubble.innerHTML = avatarHtml +
            '<div class="bubble-content">' +
            '<p>' + escapeHtml(text) + '</p>' +
            sqlHtml +
            '</div>';

        chatMessages.appendChild(bubble);
        bubble.style.animation = 'fadeInUp 0.3s ease both';
        chatMessages.scrollTop = chatMessages.scrollHeight;
        return bubble;
    }

    // Typing indicator
    function appendTypingIndicator() {
        const el = document.createElement('div');
        el.className = 'chat-bubble assistant-bubble typing-indicator';
        el.innerHTML = '<div class="bubble-avatar">' +
            '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
            '<path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>' +
            '</svg></div>' +
            '<div class="bubble-content"><div class="typing-dots">' +
            '<span></span><span></span><span></span>' +
            '</div></div>';
        chatMessages.appendChild(el);
        chatMessages.scrollTop = chatMessages.scrollHeight;
        return el;
    }

    // Chat loading state
    function setChatLoading(loading) {
        chatSendBtn.disabled = loading;
        chatInput.disabled = loading;
        if (loading) {
            chatSendBtn.classList.add('loading');
        } else {
            chatSendBtn.classList.remove('loading');
            chatInput.focus();
        }
    }

    // Chat event listeners
    chatSendBtn.addEventListener('click', () => sendChatMessage(chatInput.value));

    chatInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            sendChatMessage(chatInput.value);
        }
    });

    // Starter chips
    document.querySelectorAll('.chat-starter-chip').forEach(chip => {
        chip.addEventListener('click', () => {
            const prompt = chip.getAttribute('data-prompt');
            chatInput.value = prompt;
            sendChatMessage(prompt);
        });
    });

    // Clear chat
    chatClearBtn.addEventListener('click', () => {
        chatHistory = [];
        chatMessages.innerHTML = '';
        // Re-add welcome message
        const welcome = document.createElement('div');
        welcome.className = 'chat-bubble assistant-bubble';
        welcome.innerHTML = '<div class="bubble-avatar">' +
            '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
            '<path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>' +
            '</svg></div>' +
            '<div class="bubble-content"><p>Chat cleared! How can I help you?</p></div>';
        chatMessages.appendChild(welcome);
        chatStarters.classList.remove('hidden');
    });

})();

