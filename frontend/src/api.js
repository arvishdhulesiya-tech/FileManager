async function request(url, options = {}) {
  const res = await fetch(url, { credentials: 'same-origin', ...options })
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try { const body = await res.json(); message = body.error || message } catch {}
    throw new Error(message)
  }
  return res
}

export async function login(username, password) {
  const res = await request('api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ login: username, password })
  })
  return res.json()
}
export async function session() { return (await request('api/auth/session')).json() }
export async function logout() { await request('api/auth/logout', { method: 'POST' }) }
export async function listFiles(path, sort = 'name') {
  return (await request(`api/files?path=${encodeURIComponent(path || '')}&sort=${encodeURIComponent(sort)}`)).json()
}
export async function createFolder(path, name) { return jsonPost('api/files/folder', { path, name }) }
export async function createFile(path, name) { return jsonPost('api/files/file', { path, name }) }
export async function rename(path, name) { return jsonPost('api/files/rename', { path, name }) }
export async function move(paths, destination) { return jsonPost('api/files/move', { paths, destination }) }
export async function copy(paths, destination) { return jsonPost('api/files/copy', { paths, destination }) }
export async function remove(paths) { return jsonPost('api/files/delete', { paths }) }
export async function zip(paths) { const res=await request('api/files/zip', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({paths}) }); return res.blob() }
export async function downloadZip(paths, customName){ 
  const blob=await zip(paths); 
  const url=URL.createObjectURL(blob); 
  const a=document.createElement('a'); 
  a.href=url; 
  // Extract filename from path if not provided
  let filename = customName || 'FileManager-selection.zip'
  if (!customName && paths.length === 1) {
    const path = paths[0]
    // Extract the last part of the path (filename/foldername)
    const name = path.replace(/\\/g, '/').split('/').pop() || 'FileManager-selection'
    filename = `${name}.zip`
  }
  a.download = filename; 
  a.click(); 
  setTimeout(()=>URL.revokeObjectURL(url),1000) 
}
export async function readFile(path) { return (await request(`api/files/content?path=${encodeURIComponent(path)}`)).json() }
export async function saveFile(path, content, backup = false) { return jsonPost('api/files/content', { path, content, backup }) }
export async function unpack(path) { return jsonPost('api/files/unpack', { path }) }
export async function unzip(zipPath) { return jsonPost('api/files/unpack', { path: zipPath }) }
export async function command(path, commandText) { return jsonPost('api/command', { path, command: commandText }) }

/**
 * ✅ ENHANCED: Support for file overwrite on upload
 * @param {string} path - Destination path
 * @param {FileList} files - Files to upload
 * @param {function} onProgress - Progress callback (optional)
 * @param {boolean} overwrite - Allow overwriting existing files (default: true)
 */
export async function upload(path, files, onProgress, overwrite = true) {
  const form = new FormData()
  form.append('path', path)
  // ✅ NEW: Add overwrite parameter to API
  form.append('overwrite', overwrite ? 'true' : 'false')
  
  for (const file of files) form.append('files', file)
  
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', 'api/files/upload')
    xhr.withCredentials = true
    
    // ✅ Progress tracking with smooth animation
    xhr.upload.onprogress = e => {
      if (e.lengthComputable) {
        const progress = Math.round(e.loaded * 100 / e.total)
        onProgress?.(progress)
      }
    }
    
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText))
      } else {
        reject(new Error(xhr.responseText || `Upload failed (${xhr.status})`))
      }
    }
    
    xhr.onerror = () => reject(new Error('Upload failed'))
    xhr.ontimeout = () => reject(new Error('Upload timeout'))
    
    xhr.send(form)
  })
}

export function downloadUrl(path) { return `api/files/download?path=${encodeURIComponent(path)}` }

async function jsonPost(url, body) {
  const res = await request(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  return res.json()
}