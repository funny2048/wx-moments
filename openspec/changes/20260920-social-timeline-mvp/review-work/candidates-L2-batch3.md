# finder=L2 batch-3（接入层+静态页）
```json
[
  {"file":"moments-web/src/main/resources/static/assets/app.js","line":57,"lens":"L2","severity":"LOW","claim":"getQueryParam 对畸形百分号编码查询值调用 decodeURIComponent 抛未捕获 URIError，整页脚本初始化中断","evidence":{"trigger":"/feed.html?viewerId=% 或 /friend-detail.html?userId=%zz —— decodeURIComponent(match[1]) 抛 URIError；feed.html:59 needViewer() 在 loadMore 的 try/catch 之外被调用","failure":"页面脚本中断：Feed 永久停在加载中、friend-detail 初始化 IIFE 中止；仅影响构造该 URL 的用户自身（自伤型），故 LOW"}}
]
```
note: 本批无 B/H/M；XSS 全量核对均过 App.esc()/avatarSrc()；越权类经 api.md §1.2/接口11/17 核实为既定设计未列候选。
