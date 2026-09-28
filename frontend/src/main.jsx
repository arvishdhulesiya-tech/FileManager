import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Archive, ChevronLeft, ChevronRight, Command, Download, File, FileEdit, FilePlus, Folder, FolderPlus, HardDrive, LogOut, Pencil, Plus, RefreshCw, Search, Trash2, UploadCloud, X, FileUp } from 'lucide-react'
import * as api from './api'
import './styles.css'

const humanSize = n => n == null ? '—' : n < 1024 ? `${n} B` : n < 1024**2 ? `${(n/1024).toFixed(1)} KB` : `${(n/1024**2).toFixed(1)} MB`
const typeOf = f => f.directory ? 'Folder' : (f.name.split('.').pop() || 'File').toUpperCase()
const isZip = f => f.name.toLowerCase().endsWith('.zip')

// ============================================================
// PATH SELECTOR COMPONENT - QUICK BUTTONS
// ============================================================
function PathSelector({ currentPath, onPathChange, roots=[] }) {
  return <div className="path-selector">
    {roots.map(root => <button key={root} className={`path-btn ${currentPath === root ? 'active' : ''}`} onClick={() => onPathChange(root)} title={`Browse ${root}`}>
      <HardDrive size={16} />
      {root}
    </button>)}
  </div>
}

// ============================================================
// LOGIN COMPONENT
// ============================================================
function Login({ onLogin, message }) {
  const [user,setUser]=useState('admin'), [pass,setPass]=useState(''), [busy,setBusy]=useState(false), [error,setError]=useState(message||'')
  async function submit(e){ e.preventDefault(); setBusy(true); setError(''); try{ const result = await api.login(user,pass); onLogin(result.username || user) }catch(err){setError(err.message)}finally{setBusy(false)} }
  return <div className="login-shell"><form className="login-card" onSubmit={submit}><div className="brand-mark">FM</div><h1>FileManager</h1><p>Secure server file management</p>{error&&<div className="alert error">{error}</div>}<label>Username<input value={user} onChange={e=>setUser(e.target.value)} autoFocus /></label><label>Password<input type="password" value={pass} onChange={e=>setPass(e.target.value)} /></label><button className="primary full" disabled={busy}>{busy?'Signing in…':'Sign in'}</button></form></div>
}

// ============================================================
// MODAL COMPONENT
// ============================================================
function Modal({title,children,onClose,actions}){return <div className="modal-backdrop"><div className="modal"><div className="modal-head"><h3>{title}</h3><button className="icon" onClick={onClose}><X size={18}/></button></div><div className="modal-body">{children}</div><div className="modal-actions">{actions}</div></div></div>}

// ============================================================
// MAIN APP COMPONENT
// ============================================================
function App(){
 const [logged,setLogged]=useState(null), [loginMsg,setLoginMsg]=useState(''), [path,setPath]=useState(''), [data,setData]=useState({entries:[],readOnly:false,allowUpload:true,nativeCommands:false}), [selected,setSelected]=useState([]), [search,setSearch]=useState(''), [sort,setSort]=useState('name'), [error,setError]=useState(''), [message,setMessage]=useState(''), [modal,setModal]=useState(null), [editor,setEditor]=useState(null), [viewer,setViewer]=useState(null), [uploadPct,setUploadPct]=useState(null)
 
 useEffect(()=>{api.session().then(x=>{setLogged(x.authenticated); if(x.username) setData(d=>({...d,username:x.username}))}).catch(()=>setLogged(false))},[])
 
 const refresh=()=>api.listFiles(path,sort).then(d=>{setData(d); if(!path&&d.path)setPath(d.path)}).catch(e=>setError(e.message))
 useEffect(()=>{if(logged)refresh()},[logged,path,sort])
 const entries=useMemo(()=>data.entries.filter(f=>f.name.toLowerCase().includes(search.toLowerCase())),[data.entries,search])
 if(logged===null)return <div className="loading">Loading…</div>
 if(!logged)return <Login message={loginMsg} onLogin={username=>{setData(d=>({...d,username}));setLogged(true)}}/>
 const selectedItems=data.entries.filter(x=>selected.includes(x.path))
 const clear=()=>{setSelected([]);setModal(null)}
 async function run(fn){try{setError('');const r=await fn();if(r?.message)setMessage(r.message);await refresh();setSelected([]);setModal(null)}catch(e){setError(e.message)}}
 
 function normalizePath(v){ return (v||'').replace(/\\/g,'/').replace(/\/+$/,'') || '/' }
 function isWithinRoot(candidate, root){
   const c=normalizePath(candidate)
   const r=normalizePath(root)
   if(/^[A-Za-z]:/.test(r)) return c.toLowerCase()===r.toLowerCase() || c.toLowerCase().startsWith(r.toLowerCase()+'/')
   return c===r || c.startsWith(r.endsWith('/')?r:r+'/')
 }
 function navUp(){
   const roots=data.roots||[]
   if(!path)return
   const normalized=normalizePath(path)
   const matchingRoot=roots.find(r=>isWithinRoot(normalized,r))
   if(matchingRoot && normalizePath(normalized).toLowerCase()===normalizePath(matchingRoot).toLowerCase()) return
   const idx=normalized.lastIndexOf('/')
   if(idx<0)return
   let newPath=normalized.slice(0,idx)
   if(/^[A-Za-z]:$/.test(newPath)) newPath+='/'
   if(matchingRoot && !isWithinRoot(newPath,matchingRoot)) return
   setPath(newPath.replace(/^([A-Za-z]):\//,'$1\\'))
 }
 
 // Breadcrumb is constrained to the configured FileManager roots.
 function breadcrumb(){
   const isWin = /^[A-Za-z]:/.test(path)
   const normalized = normalizePath(path)
   const roots=data.roots||[]
   const matchingRoot=roots.find(r=>isWithinRoot(normalized,r)) || roots[0] || path
   const rootNormalized=normalizePath(matchingRoot)
   const rootLabel=matchingRoot
   const relative=normalized.slice(rootNormalized.length).replace(/^\//,'')
   const remaining=relative?relative.split('/').filter(Boolean):[]
   const rootPath=isWin?rootNormalized.replace(/^([A-Za-z]):\//,'$1\\'):rootNormalized
   
   return <div className="breadcrumbs">
     <button onClick={() => setPath(rootPath)}>
       {rootLabel}
     </button>
     {remaining.map((p, i) => {
       const accumulated = rootNormalized + '/' + remaining.slice(0, i + 1).join('/')
       const target = isWin ? accumulated.replace(/\//g,'\\') : accumulated
       
       return <React.Fragment key={i}>
         <ChevronRight size={15} />
         <button onClick={() => setPath(target)}>{p}</button>
       </React.Fragment>
     })}
   </div>
 }
 
 return <div className="app"><header><div className="top-brand"><div className="brand-mark small">FM</div><strong>FileManager</strong></div><div className="header-actions"><span className="user-pill">{data.username || 'admin'}</span><button className="icon" title="Logout" onClick={()=>api.logout().then(()=>setLogged(false))}><LogOut size={18}/></button></div></header>
 
 <main>
   {/* ✅ SOLUTION 3: Quick Path Selector Buttons */}
   <PathSelector currentPath={path} onPathChange={setPath} roots={data.roots || (data.path ? [data.path] : [])} />
   
   <div className="toolbar">
     <div className="toolbar-left">
       <button className="icon" onClick={navUp} disabled={!path}><ChevronLeft/></button>
       <button className="icon" onClick={refresh}><RefreshCw size={18}/></button>
       <div className="search"><Search size={17}/><input placeholder="Search files…" value={search} onChange={e=>setSearch(e.target.value)}/></div>
     </div>
     <div className="toolbar-right">
       <button className="secondary" onClick={()=>setModal({type:'upload'})} disabled={!data.allowUpload}><UploadCloud size={17}/> Upload</button>
       <button className="primary" onClick={()=>setModal({type:'new'})}><Plus size={17}/> New</button>
     </div>
   </div>
   
   {breadcrumb()}
   {(error||message)&&<div className={`alert ${error?'error':'success'}`}>{error||message}<button className="close-alert" onClick={()=>{setError('');setMessage('')}}><X size={15}/></button></div>}
   <div className="selectionbar">{selectedItems.length?<><strong>{selectedItems.length} selected</strong><button onClick={()=>setModal({type:'rename'})}><Pencil size={15}/> Rename</button><button onClick={()=>setModal({type:'copy'})}><Archive size={15}/> Copy</button><button onClick={()=>setModal({type:'move'})}><ChevronRight size={15}/> Move</button><button className="danger-text" onClick={()=>setModal({type:'delete'})}><Trash2 size={15}/> Delete</button><button onClick={async()=>{try{const name=selected.length===1?(selectedItems[0].name+'.zip'):undefined;await api.downloadZip(selected,name)}catch(e){setError(e.message)}}}><Download size={15}/> ZIP</button></>:<span>{entries.length} items</span>}</div>
   <div className="table"><div className="thead"><div className="check"><input type="checkbox" checked={entries.length>0&&entries.every(x=>selected.includes(x.path))} onChange={e=>setSelected(e.target.checked?entries.map(x=>x.path):[])}/></div><button onClick={()=>setSort('name')}>Name</button><button onClick={()=>setSort('size')}>Size</button><button onClick={()=>setSort('date')}>Modified</button><div></div></div>
   {entries.map(f=><div className="row" key={f.path} onDoubleClick={()=>!f.directory&&!isZip(f)&&openEditor(f)}><div className="check"><input type="checkbox" checked={selected.includes(f.path)} onChange={e=>setSelected(s=>e.target.checked?[...s,f.path]:s.filter(x=>x!==f.path))}/></div><div className="name-cell"><span className="file-icon">{f.directory?<Folder size={19}/>:<File size={19}/>}</span><span className={f.directory?'folder-link':''} onClick={e=>{if(f.directory){e.stopPropagation();setSearch('');setPath(f.path)}else{e.stopPropagation();openViewer(f)}}}>{f.name}</span></div><div>{f.directory?'—':humanSize(f.size)}</div><div>{f.modified||'—'}</div><div className="row-actions">{f.directory?<><button className="icon" title="Open folder" onClick={()=>{setSearch('');setPath(f.path)}}><Folder size={17}/></button><button className="icon" title="Download as ZIP" onClick={async()=>{try{await api.downloadZip([f.path], `${f.name}.zip`)}catch(e){setError(e.message)}}}><Archive size={17}/></button></>:isZip(f)?<><button className="icon" title="Unzip to current directory" onClick={async()=>{try{await api.unzip(f.path);setMessage(`Extracted ${f.name} successfully`);await refresh()}catch(e){setError(e.message)}}}><FileUp size={17}/></button><button className="icon" title="Download" onClick={()=>{window.location=api.downloadUrl(f.path)}}><Download size={17}/></button></>:<><button className="icon" title="Edit" onClick={()=>openEditor(f)}><FileEdit size={17}/></button><button className="icon" title="Download" onClick={()=>{window.location=api.downloadUrl(f.path)}}><Download size={17}/></button></>}<button className="icon" title="Rename" onClick={()=>{setSelected([f.path]);setModal({type:'rename'})}}><Pencil size={17}/></button><button className="icon danger-icon" title="Delete" onClick={()=>{setSelected([f.path]);setModal({type:'delete'})}}><Trash2 size={17}/></button></div></div>)}
   {entries.length===0&&<div className="empty"><Folder size={42}/><h3>No files found</h3><p>This directory is empty or your search returned no results.</p></div>}</div>
 </main>
 
 {uploadPct!==null&&<div className="upload-toast"><UploadCloud size={18}/><div><strong>Uploading…</strong><div className="progress"><span style={{width:`${uploadPct}%`}}/></div></div><b>{uploadPct}%</b></div>}
 
 {modal?.type==='new'&&<Modal title="Create new" onClose={clear} actions={<button className="secondary" onClick={clear}>Cancel</button>}><div className="choice-grid"><button onClick={()=>setModal({type:'create-folder'})}><FolderPlus/>Folder</button><button onClick={()=>setModal({type:'create-file'})}><FilePlus/>File</button></div></Modal>}
 {['create-folder','create-file'].includes(modal?.type)&&(
   <NameModal
    title={modal.type==='create-folder'?'New folder':'New file'}
    onClose={clear}
    onSave={name=>run(
     ()=>modal.type==='create-folder'
      ?api.createFolder(path,name)
      :api.createFile(path,name)
    )}
   />
  )}
 {modal?.type==='rename'&&selectedItems.length===1&&<NameModal title="Rename" initial={selectedItems[0].name} onClose={clear} onSave={name=>run(()=>api.rename(selectedItems[0].path,name))}/>} 
 {modal?.type==='delete'&&<Modal title="Delete selected items?" onClose={clear} actions={<><button className="secondary" onClick={clear}>Cancel</button><button className="danger" onClick={()=>run(()=>api.remove(selected))}>Delete</button></>}><p>This action cannot be undone.</p></Modal>}
 {modal?.type==='move'&&<NameModal title="Move to directory" initial={path} onClose={clear} onSave={dest=>run(()=>api.move(selected,dest))}/>}
 {modal?.type==='copy'&&<NameModal title="Copy to directory" initial={path} onClose={clear} onSave={dest=>run(()=>api.copy(selected,dest))}/>} 
 {modal?.type==='upload'&&<UploadModal path={path} onClose={clear} onUpload={files=>{setUploadPct(0);api.upload(path,files,setUploadPct).then(r=>{setMessage(r.message||'Upload complete');setUploadPct(null);setModal(null);refresh()}).catch(e=>{setError(e.message);setUploadPct(null)})}}/>}
 {editor&&<Editor file={editor} onClose={()=>setEditor(null)} onSaved={()=>{setEditor(null);refresh()}}/>}
 {viewer&&<Viewer file={viewer} onClose={()=>setViewer(null)} onEdit={()=>{setViewer(null);openEditor(viewer)}}/>}
 </div>
 function openEditor(f){if(f.directory)return;api.readFile(f.path).then(c=>setEditor({...f,content:c.content})).catch(e=>setError(e.message))}
 function openViewer(f){if(f.directory)return;api.readFile(f.path).then(c=>setViewer({...f,content:c.content})).catch(e=>setError(e.message))}
}

function NameModal({title,initial='',onClose,onSave}){const [v,setV]=useState(initial);return <Modal title={title} onClose={onClose} actions={<><button className="secondary" onClick={onClose}>Cancel</button><button className="primary" onClick={()=>v.trim()&&onSave(v.trim())}>Save</button></>}><input className="modal-input" value={v} onChange={e=>setV(e.target.value)} autoFocus onKeyDown={e=>e.key==='Enter'&&v.trim()&&onSave(v.trim())}/></Modal>}

// ✅ ENHANCED: UploadModal now shows selected filenames with sizes
function UploadModal({onClose,onUpload}){
  const [files,setFiles]=useState([])
  const totalSize = Array.from(files).reduce((sum, f) => sum + f.size, 0)
  
  return <Modal title="Upload files" onClose={onClose} actions={<><button className="secondary" onClick={onClose}>Cancel</button><button className="primary" disabled={!files.length} onClick={()=>onUpload(files)}>Upload</button></>}>
    <label className="dropzone">
      <UploadCloud size={30}/>
      <strong>Select files</strong>
      <span>Multiple files are supported</span>
      <input type="file" multiple onChange={e=>setFiles([...e.target.files])}/>
    </label>
    
    {/* ✅ NEW: Show selected files list */}
    {files.length>0&&(
      <div style={{marginTop:'16px', paddingTop:'16px', borderTop:'1px solid #e9edf3'}}>
        <div style={{marginBottom:'10px', fontSize:'13px', color:'#667085'}}>
          <strong style={{color:'#1d2939'}}>{files.length} file(s) selected</strong> • {humanSize(totalSize)}
        </div>
        
        {/* ✅ File list */}
        <div style={{maxHeight:'200px', overflowY:'auto', border:'1px solid #e1e6ee', borderRadius:'8px', padding:'8px'}}>
          {Array.from(files).map((f, i) => (
            <div key={i} style={{
              display:'flex',
              justifyContent:'space-between',
              alignItems:'center',
              padding:'8px 10px',
              borderBottom: i < files.length - 1 ? '1px solid #eef1f5' : 'none',
              fontSize:'13px'
            }}>
              <div style={{display:'flex', alignItems:'center', gap:'8px', flex:1, minWidth:0}}>
                <File size={16} style={{color:'#4165d6', flexShrink:0}}/>
                <span style={{
                  color:'#1d2939',
                  fontWeight:'500',
                  whiteSpace:'nowrap',
                  overflow:'hidden',
                  textOverflow:'ellipsis',
                  title: f.name
                }}>
                  {f.name}
                </span>
              </div>
              <span style={{color:'#98a2b3', fontSize:'12px', marginLeft:'8px', flexShrink:0}}>
                {humanSize(f.size)}
              </span>
            </div>
          ))}
        </div>
      </div>
    )}
  </Modal>
}

function ContextMenu({file,onClose,onOpen,onDownload,onDownloadZip,onRename,onDelete,onEdit}){return <Modal title={file.name} onClose={onClose} actions={<button className="secondary" onClick={onClose}>Close</button>}><div className="menu-grid">{file.directory?<><button onClick={onOpen}><Folder/> Open</button><button onClick={onDownloadZip}><Archive/> Download as ZIP</button></>:<><button onClick={onEdit}><FileEdit/> Edit</button><button onClick={onDownload}><Download/> Download</button></>}<button onClick={onRename}><Pencil/> Rename</button><button className="danger-text" onClick={onDelete}><Trash2/> Delete</button></div></Modal>}

function Viewer({file,onClose,onEdit}){return <div className="editor-shell"><div className="editor-head"><div><strong>{file.name}</strong><span>{file.path}</span></div><div><button className="secondary" onClick={onClose}>Close</button><button className="primary" onClick={onEdit}>Edit</button></div></div><textarea className="editor-text" value={file.content||''} onChange={()=>{}} spellCheck="false" readOnly/></div>}

function Editor({file,onClose,onSaved}){const [text,setText]=useState(file.content||''),[backup,setBackup]=useState(true),[busy,setBusy]=useState(false),[err,setErr]=useState('');async function save(){setBusy(true);try{await api.saveFile(file.path,text,backup);onSaved()}catch(e){setErr(e.message)}finally{setBusy(false)}}return <div className="editor-shell"><div className="editor-head"><div><strong>{file.name}</strong><span>{file.path}</span></div><div><label className="checklabel"><input type="checkbox" checked={backup} onChange={e=>setBackup(e.target.checked)}/> Backup</label><button className="secondary" onClick={onClose}>Cancel</button><button className="primary" onClick={save}>{busy?'Saving…':'Save'}</button></div></div>{err&&<div className="alert error">{err}</div>}<textarea className="editor-text" value={text} onChange={e=>setText(e.target.value)} spellCheck="false"/></div>}

createRoot(document.getElementById('root')).render(<App/>)