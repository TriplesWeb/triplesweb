import json,subprocess,re,urllib.request,urllib.parse,http.cookiejar,urllib.error
info=json.loads(subprocess.check_output(["docker","inspect","triples-pr26-preview"],text=True))[0]
env=dict(v.split("=",1) for v in info["Config"]["Env"] if "=" in v)
logs=subprocess.run(["docker","logs","--tail","500","triples-pr26-mobile-tunnel"],capture_output=True,text=True)
url=re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com",logs.stdout+logs.stderr)[-1]
jar=http.cookiejar.CookieJar()
client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
with client.open(url+"/__preview/login",timeout=20) as response:
    csrf=re.search(r'name="csrf" value="([^"]+)"',response.read().decode()).group(1)
with client.open(urllib.request.Request(url+"/__preview/login",data=urllib.parse.urlencode({"password":env["CHEMISTRY_ADMIN_PASSWORD"],"csrf":csrf}).encode()),timeout=30) as response:response.read()
base=url+"/cosmo/objekt/detail/hayeoncc367z"
try:
    with client.open(urllib.request.Request(base,headers={"Accept":"application/json"}),timeout=30) as response:item=json.load(response)
    csrf=next(c.value for c in jar if c.name.endswith("XSRF-TOKEN"))
    request=urllib.request.Request(base+"/favorite?favorite=true",data=b"",method="PUT",headers={"Accept":"application/json","X-XSRF-TOKEN":urllib.parse.unquote(csrf)})
    with client.open(request,timeout=20) as response:
        print("FAVORITE_SAVE_HTTP="+str(response.status));print("FAVORITE_SAVED="+str(json.load(response).get("favorite")))
    with client.open(urllib.request.Request(base+"/favorite?favorite=false",data=b"",method="PUT",headers={"Accept":"application/json","X-XSRF-TOKEN":urllib.parse.unquote(csrf)}),timeout=20) as response:response.read()
except urllib.error.HTTPError as error:
    print("FAVORITE_ERROR_HTTP="+str(error.code))
errors=subprocess.run(["docker","logs","--tail","1500","triples-pr26-preview"],capture_output=True,text=True)
for line in (errors.stdout+errors.stderr).splitlines():
    if any(word in line for word in ("Data too long","SQLSyntaxErrorException","Caused by:","ERROR")):
        print(line[:450])
