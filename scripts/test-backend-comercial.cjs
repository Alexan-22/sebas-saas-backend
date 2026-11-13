// Ejecuta únicamente pruebas auditadas: nunca carga migraciones ni perfiles de producción.
const {spawnSync}=require('child_process');
const path=require('path');
const fs=require('fs');
const {pathToFileURL}=require('url');
const env={...process.env};
for(const key of Object.keys(env)){
 const name=key.toUpperCase().replace(/[.-]/g,'_');
 if(/^(SPRING_DATASOURCE_|SPRING_JPA_|SPRING_FLYWAY_|SPRING_LIQUIBASE_|SPRING_SQL_INIT_|SPRING_CONFIG_|SPRING_PROFILES_|HIBERNATE_|JAKARTA_PERSISTENCE_|SUPABASE_)/.test(name)
 || ['DB_URL','DATABASE_URL','JDBC_URL','SPRING_APPLICATION_JSON','DB_USERNAME','DB_USER','DB_PASSWORD','DATABASE_USERNAME','DATABASE_PASSWORD','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','MAVEN_OPTS','MAVEN_ARGS'].includes(name)) delete env[key];
}
const jdkOrigen=path.join(process.env.USERPROFILE,'.jdks','ms-21.0.12.1');
const jdkLocal=path.resolve(__dirname,'..','target','comercial-jdk21');
if(!fs.existsSync(path.join(jdkLocal,'bin','java.exe')))fs.cpSync(jdkOrigen,jdkLocal,{recursive:true,dereference:true,errorOnExist:true,force:false});
env.JAVA_HOME=jdkLocal;
env.Path=path.join(env.JAVA_HOME,'bin')+path.delimiter+(env.Path||env.PATH||'');
const cases=['SuscripcionPlanComercialTest','RbacEmpleadoGestionTest','RbacRevocacionTenantTest','JwtCredencialesRevocacionTest'];
if(!process.argv.includes('--unit'))cases.push('ComercialHttpPostgresTest');
const raiz=path.join(process.env.USERPROFILE,'.m2','wrapper','dists','apache-maven-3.9.16');
const mvn=fs.readdirSync(raiz).map(d=>path.join(raiz,d,'bin','mvn.cmd')).find(p=>fs.existsSync(p));
if(!mvn)throw new Error('Se requiere Maven 3.9.16 instalado en la caché local');
const repo=path.resolve(__dirname,'..','target','comercial-maven-repository');
const settings=path.resolve(__dirname,'..','target','comercial-maven-settings.xml');
fs.mkdirSync(repo,{recursive:true});
const origen=pathToFileURL(path.join(process.env.USERPROFILE,'.m2','repository')+path.sep).href.replaceAll('&','&amp;');
fs.writeFileSync(settings,'<settings><mirrors><mirror><id>cache-local</id><mirrorOf>*</mirrorOf><url>'+origen+'</url></mirror></mirrors></settings>');
const quote=s=>"'"+s.replaceAll("'","''")+"'";
const comando="& "+quote(mvn)+" -B -s "+quote(settings)+" "+quote('-Dmaven.repo.local='+repo)+" "+quote('-Duser.home='+process.env.USERPROFILE)+" "+quote('-Dtest='+cases.join(','))+" test; exit $LASTEXITCODE";
const result=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',comando],{env,stdio:'inherit',cwd:path.resolve(__dirname,'..')});
if(result.error)throw result.error;process.exitCode=result.status??1;
