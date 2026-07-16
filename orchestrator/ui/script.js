let isSessionActive = false;

async function startSession() {
    document.getElementById('btn-start').disabled = true;
    document.getElementById('btn-start').textContent = "Starting Tunnel...";
    
    // Call the Python backend to start ngrok and the C++ server
    await pywebview.api.start_remote_session();
}

window.onRemoteSessionStarted = function(result) {
    if (result.success) {
        isSessionActive = true;
        document.getElementById('btn-start').classList.add('hidden');
        document.getElementById('btn-stop').classList.remove('hidden');
        
        const codeContainer = document.getElementById('code-container');
        codeContainer.classList.remove('hidden');
        
        document.getElementById('support-code').textContent = result.code;
        document.getElementById('btn-copy').classList.remove('hidden');
    } else {
        document.getElementById('support-code').textContent = "Error: " + result.error;
        document.getElementById('code-container').classList.remove('hidden');
        document.getElementById('btn-start').disabled = false;
        document.getElementById('btn-start').textContent = "Try Again";
    }
};

function copyCode() {
    const code = document.getElementById('support-code').textContent;
    navigator.clipboard.writeText(code).then(() => {
        const btn = document.getElementById('btn-copy');
        btn.textContent = "Copied!";
        setTimeout(() => { btn.textContent = "Copy Code"; }, 2000);
    });
}

async function stopSession() {
    await pywebview.api.stop_remote_session();
    isSessionActive = false;
    
    document.getElementById('btn-stop').classList.add('hidden');
    document.getElementById('btn-start').classList.remove('hidden');
    document.getElementById('btn-start').disabled = false;
    document.getElementById('btn-start').textContent = "Start Session";
    
    document.getElementById('code-container').classList.add('hidden');
    document.getElementById('support-code').textContent = "Loading...";
}
