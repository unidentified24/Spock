const fs=require('node:fs');const vm=require('node:vm');const assert=require('node:assert/strict');const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/metrolist/music/utils/potoken/PoTokenWebView.kt'),'utf8');
const part=source.slice(source.indexOf('suspend fun generatePoToken'));
const template=part.match(/"""(try \{[\s\S]*?)"""/)[1];
async function exercise(old=false,reject=false){
 const waits=new Map(),results=[];
 const context=vm.createContext({obtainPoToken:bytes=>new Promise((resolve,fail)=>waits.set(String.fromCharCode(...bytes),{resolve,fail})),bridge:{onObtainPoTokenResult:(id,token)=>results.push([id,token]),onObtainPoTokenError:id=>results.push([id,'error'])}});
 for(const id of ['track_AAAAA','track_BBBBB']){
  let code=template.replaceAll('${stringToU8(identifier)}',JSON.stringify([...id].map(c=>c.charCodeAt(0)))).replaceAll('$identifier',id).replaceAll('$JS_INTERFACE','bridge');
  if(old)code=code.replaceAll('const identifier =','identifier =').replaceAll('const u8Identifier =','u8Identifier =').replaceAll('const poTokenU8String =','poTokenU8String =');
  vm.runInContext(code,context);
 }
 waits.get('track_BBBBB').resolve([2]);
 if(reject)waits.get('track_AAAAA').fail(new Error('test'));else waits.get('track_AAAAA').resolve([1]);
 for(let i=0;i<5;i++)await Promise.resolve();
 return results;
}
(async()=>{
 assert.deepEqual(await exercise(),[['track_BBBBB','2'],['track_AAAAA','1']]);
 assert.notDeepEqual(await exercise(true),[['track_BBBBB','2'],['track_AAAAA','1']]);
 assert.deepEqual(await exercise(false,true),[['track_BBBBB','2'],['track_AAAAA','error']]);
 console.log('Actual token bridge: 2 concurrency/error checks passed; previous global-variable bug reproduced');
})().catch(e=>{console.error(e);process.exitCode=1;});
