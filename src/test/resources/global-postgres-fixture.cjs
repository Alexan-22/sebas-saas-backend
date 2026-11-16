// Fixture de test: no admite URL, host, puerto ni credenciales externos.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),net=require('node:net'),crypto=require('node:crypto');
const bin='C:/Program Files/PostgreSQL/17/bin';
const target=path.resolve('target');fs.mkdirSync(target,{recursive:true});const targetReal=fs.realpathSync(target);
const base=path.join(targetReal,'global-node-postgres');fs.mkdirSync(base,{recursive:true});
if(path.dirname(fs.realpathSync(base))!==targetReal)throw Error('Base de fixture fuera de target');
const root=fs.mkdtempSync(path.join(base,'fixture-'));if(path.dirname(fs.realpathSync(root))!==fs.realpathSync(base))throw Error('Raiz de fixture no verificada');
const data=path.join(root,'data'),username='global_node_fixture',password=crypto.randomUUID(),clusterId=crypto.randomUUID();
const env={SystemRoot:'C:/Windows',WINDIR:'C:/Windows',Path:bin+';C:/Windows/System32;C:/Windows',TEMP:root,TMP:root};
let port,server,started=false,stopped=false;
function exe(name,args,withPassword=false){const r=cp.spawnSync(path.join(bin,name+'.exe'),args,{cwd:root,env:withPassword?{...env,PGPASSWORD:password}:env,encoding:'utf8',timeout:30000,windowsHide:true});if(r.error||r.status!==0)throw Error('Fallo local en '+name);return r.stdout.trim();}
function stop(){if(stopped||!started)return;stopped=true;try{exe('pg_ctl',['-D',data,'-m','fast','-w','stop']);}catch(e){if(server&&server.exitCode===null)server.kill();process.exitCode=1;}}
process.on('exit',stop);process.on('SIGTERM',()=>{stop();process.exit(1);});
(async()=>{try{
 for(const p of ['initdb','postgres','pg_ctl','psql'])if(!fs.statSync(path.join(bin,p+'.exe')).isFile())throw Error('PostgreSQL17 requerido');
 port=await new Promise((resolve,reject)=>{const socket=net.createServer();socket.once('error',reject);socket.listen(0,'127.0.0.1',()=>{const p=socket.address().port;socket.close(()=>resolve(p));});});
 const pw=path.join(root,'init-password.txt');fs.writeFileSync(pw,password);try{exe('initdb',['-D',data,'--username='+username,'--auth=scram-sha-256','--pwfile='+pw,'--encoding=UTF8','--locale=C']);}finally{fs.unlinkSync(pw);}
 const dataReal=fs.realpathSync(data);if(path.dirname(dataReal)!==fs.realpathSync(root))throw Error('Datos fuera de fixture');
 const log=fs.openSync(path.join(root,'postgres.log'),'w');server=cp.spawn(path.join(bin,'postgres.exe'),['-D',dataReal,'-h','127.0.0.1','-p',String(port),'-c','cluster_name='+clusterId,'-c','log_statement=none','-c','log_min_error_statement=panic'],{cwd:root,env,stdio:['ignore',log,log],windowsHide:true});fs.closeSync(log);started=true;
 let proof;for(let n=0;n<60;n++){try{proof=exe('psql',['-X','--no-password','-h','127.0.0.1','-p',String(port),'-U',username,'-d','postgres','-A','-t','-c',"SELECT current_setting('data_directory') || '|' || current_setting('cluster_name')"],true);break;}catch(e){if(server.exitCode!==null)throw Error('Servidor termino durante arranque');await new Promise(r=>setTimeout(r,500));}}
 if(!proof)throw Error('Servidor local no disponible');const [actualDir,actualCluster]=proof.split('|');if(fs.realpathSync(actualDir)!==dataReal||actualCluster!==clusterId)throw Error('Identidad del servidor no coincide');
 // Este canal se lee en memoria por el test; nunca registrar la linea de handshake.
 process.stdout.write(JSON.stringify({port,username,password,dataDirectory:dataReal,clusterId})+'\n');
 process.stdin.setEncoding('utf8');let command='';process.stdin.on('data',s=>{command+=s;if(command.includes('\n')){if(command.trim()!=='STOP'){stop();process.exit(1);}stop();process.stdin.pause();process.exit(process.exitCode||0);}});
 process.stdin.on('end',()=>{stop();process.exit(process.exitCode||0);});
}catch(e){stop();process.stderr.write('Fixture local no iniciada: '+e.message+'\n');process.exit(1);}})();
