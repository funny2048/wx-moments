/* ============================================================
 * 朋友圈实验室 · 公共脚本
 * 能力：fetch 封装（ApiResult 解包 + _appId/viewerId 注入）/ Toast /
 *      用户切换器（Cookie mockUserId）/ 按钮 loading 防双击 / 帖子卡片与九宫格渲染
 * 约定（api.md §1）：query 参数 viewerId 优先于 Cookie mockUserId；
 *      code=0 成功，code!=0 统一 toast 提示 msg
 * ============================================================ */
(function () {
  'use strict';

  /** 调用方标识（api.md §1.2：仅日志追踪用途） */
  var APP_ID = 'moments-web';

  /** 当前视角 Cookie 名（design A3/C3，与后端 CurrentUserResolver 一致） */
  var VIEWER_COOKIE = 'mockUserId';

  /** 头像/图片加载失败兜底图（灰底"友"字 SVG，离线可用） */
  var DEFAULT_AVATAR = 'data:image/svg+xml;utf8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80">' +
    '<rect width="80" height="80" fill="#d5d9e0"/>' +
    '<text x="40" y="50" font-size="34" text-anchor="middle" fill="#ffffff" font-family="sans-serif">友</text></svg>');

  /**
   * 部署上下文前缀（含尾斜杠）：如页面在 /api/feed.html，则 BASE='/api/'。
   * 页面与 API 同源同上下文托管，请求路径统一叠加上下文，避免绝对路径丢前缀。
   */
  var BASE = window.location.pathname.replace(/[^/]*$/, '');

  /** API 路径适配：'/api/users' → BASE + 'api/users'（保持 api.md 路由契约，兼容任意 context-path） */
  function apiUrl(path) {
    if (path.charAt(0) !== '/') path = '/' + path;
    return BASE + path.replace(/^\//, '');
  }

  /** 资源路径适配：服务端返回的 '/images/xxx' 绝对路径叠加部署上下文；完整 URL/相对路径原样返回 */
  function assetUrl(url) {
    if (!url) return url;
    if (/^(https?:)?\/\//i.test(url) || url.charAt(0) !== '/') return url;
    return BASE + url.replace(/^\//, '');
  }

  /* ---------------- 基础工具 ---------------- */

  /** HTML 转义：所有后端数据渲染前必须经过（XSS 防护） */
  function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function getQueryParam(name) {
    var match = window.location.search.match(new RegExp('[?&]' + name + '=([^&]*)'));
    return match ? decodeURIComponent(match[1]) : null;
  }

  function readCookie(name) {
    var match = document.cookie.match(new RegExp('(?:^|;\\s*)' + name + '=([^;]*)'));
    return match ? match[1] : null;
  }

  /**
   * 当前视角用户（返回字符串 userId 或 null）。
   * 语义与后端 CurrentUserResolver 对齐：URL viewerId 参数优先，回退 Cookie；
   * 仅返回合法值（1-18 位纯数字且 >0），脏值视为未选择（后端宽松口径）。
   */
  function currentViewer() {
    var fromQuery = getQueryParam('viewerId');
    if (isValidUserId(fromQuery)) return fromQuery;
    var fromCookie = readCookie(VIEWER_COOKIE);
    if (isValidUserId(fromCookie)) return fromCookie;
    return null;
  }

  function isValidUserId(value) {
    return !!value && /^\d{1,18}$/.test(value) && Number(value) > 0;
  }

  /** 切换当前用户：写 Cookie 后刷新页面；清除 URL 上的 viewerId 覆盖参数避免压过新 Cookie */
  function setViewer(userId) {
    if (!isValidUserId(userId)) {
      toast('非法的用户 ID');
      return;
    }
    document.cookie = VIEWER_COOKIE + '=' + userId + ';path=/;max-age=31536000';
    var url = new URL(window.location.href);
    url.searchParams.delete('viewerId');
    window.location.href = url.toString();
  }

  /* ---------------- Toast ---------------- */

  var toastTimer = null;

  function toast(msg) {
    var el = document.getElementById('app-toast');
    if (!el) {
      el = document.createElement('div');
      el.id = 'app-toast';
      el.className = 'app-toast';
      document.body.appendChild(el);
    }
    el.textContent = msg;
    el.classList.add('show');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('show'); }, 2400);
  }

  /* ---------------- fetch 封装 ---------------- */

  /** 统一注入 _appId 与 viewerId（仅有合法视角时）query 参数，并叠加部署上下文 */
  function buildUrl(path) {
    var url = apiUrl(path);
    var extra = [];
    if (url.indexOf('_appId=') === -1) extra.push('_appId=' + APP_ID);
    var viewer = currentViewer();
    if (viewer && url.indexOf('viewerId=') === -1) extra.push('viewerId=' + viewer);
    if (extra.length > 0) {
      url += (url.indexOf('?') === -1 ? '?' : '&') + extra.join('&');
    }
    return url;
  }

  /**
   * JSON API 请求封装：解包 ApiResult（{code, msg, data}），code!=0 时 toast msg 并抛错。
   * @param {string} path 请求路径（可含 query）
   * @param {Object} options { method, body }（body 为 JS 对象，自动 JSON 序列化）
   * @return {Promise<any>} body.data
   */
  async function api(path, options) {
    options = options || {};
    var opt = { method: options.method || 'GET', credentials: 'same-origin', headers: {} };
    if (options.body !== undefined && options.body !== null) {
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(options.body);
    }
    var res;
    try {
      res = await fetch(buildUrl(path), opt);
    } catch (e) {
      toast('网络请求失败，请确认服务已启动');
      throw e;
    }
    return await unwrap(res);
  }

  /** 图片上传（multipart，禁止手工设置 Content-Type 由浏览器生成 boundary） */
  async function uploadImage(file) {
    var form = new FormData();
    form.append('file', file);
    var res;
    try {
      res = await fetch(apiUrl('/api/images/upload'), {
        method: 'POST', credentials: 'same-origin', body: form
      });
    } catch (e) {
      toast('网络请求失败，请确认服务已启动');
      throw e;
    }
    return await unwrap(res);
  }

  /** ApiResult 解包：code=0 返回 data；否则 toast msg 并抛带 code 的 Error */
  async function unwrap(res) {
    var body;
    try {
      body = await res.json();
    } catch (e) {
      toast('响应格式异常（HTTP ' + res.status + '）');
      throw e;
    }
    if (!body || typeof body.code !== 'number') {
      toast('响应结构异常');
      throw new Error('bad response');
    }
    if (body.code !== 0) {
      toast(body.msg || ('操作失败（错误码 ' + body.code + '）'));
      var err = new Error(body.msg || ('code ' + body.code));
      err.code = body.code;
      throw err;
    }
    return body.data;
  }

  /* ---------------- 按钮 loading 防双击（写操作非幂等，design §5.6 规则 5b/7） ---------------- */

  /**
   * 包装点击处理：执行期间按钮禁用并显示 loading 文案，结束/出错恢复。
   * @param {HTMLElement} btn 触发按钮
   * @param {string} loadingText 处理中文案
   * @param {Function} fn 异步处理函数
   */
  function withLoading(btn, loadingText, fn) {
    var oldText = btn.textContent;
    return async function () {
      if (btn.disabled) return;
      btn.disabled = true;
      btn.textContent = loadingText || '处理中...';
      try {
        await fn();
      } finally {
        btn.disabled = false;
        btn.textContent = oldText;
      }
    };
  }

  /* ---------------- 顶部导航 + 用户切换器 ---------------- */

  /**
   * 注入公共头部（导航栏 + 用户切换器）。页面需含 <div id="app-header"></div>。
   * @param {string} title 页面标题
   * @param {Object} opts { noBack: 首页无返回键 }
   */
  function mountHeader(title, opts) {
    opts = opts || {};
    var host = document.getElementById('app-header');
    if (!host) return;
    host.innerHTML =
      '<div class="nav-bar">' +
      (opts.noBack
        ? '<span class="nav-spacer"></span>'
        : '<a class="nav-back" href="index.html">首页</a>') +
      '<div class="nav-title">' + esc(title) + '</div>' +
      (opts.noBack
        ? '<span class="nav-spacer"></span>'
        : '<a class="nav-home" href="feed.html">Feed</a>') +
      '</div>' +
      '<div class="viewer-bar">' +
      '<span class="viewer-label">当前视角</span>' +
      '<select id="viewer-select" class="viewer-select"><option value="">加载用户中...</option></select>' +
      '<span class="viewer-hint">切换后刷新</span>' +
      '</div>';
    initSwitcher();
  }

  /** 用户切换器：GET /api/users?pageSize=500（api.md 接口1，切换器场景传 500） */
  async function initSwitcher() {
    var select = document.getElementById('viewer-select');
    if (!select) return;
    var viewer = currentViewer();
    try {
      var data = await api('/api/users?pageSize=500');
      var users = (data && data.users) || [];
      var html = '<option value="">-- 请选择用户 --</option>';
      users.forEach(function (u) {
        html += '<option value="' + esc(u.userId) + '"' +
          (String(u.userId) === viewer ? ' selected' : '') + '>' +
          esc(u.nickname) + '（' + esc(u.userId) + '）</option>';
      });
      if (viewer && !users.some(function (u) { return String(u.userId) === viewer; })) {
        html = '<option value="' + esc(viewer) + '" selected>用户 ' + esc(viewer) + '（不在前 500 名单）</option>' + html;
      }
      select.innerHTML = html;
      select.onchange = function () {
        if (select.value) setViewer(select.value);
      };
    } catch (e) {
      select.innerHTML = '<option value="">用户列表加载失败</option>';
    }
  }

  /** 需要当前用户视角的页面入口：无视角时展示页面内提示条并 toast，返回 null */
  function needViewer() {
    var viewer = currentViewer();
    if (viewer) return viewer;
    var banner = document.getElementById('viewer-required');
    if (banner) banner.style.display = 'block';
    toast('请先在顶部"当前视角"选择一个用户');
    return null;
  }

  /* ---------------- 帖子卡片 / 九宫格渲染（feed 与好友详情共用） ---------------- */

  function avatarSrc(url) {
    return url ? esc(assetUrl(url)) : DEFAULT_AVATAR;
  }

  /**
   * 九宫格：1 张大图（单列自适应）、2 张 2 列、4 张 2x2、其余 3 列（微信布局）。
   * @param {string[]} urls 图片 URL 数组（1-9 张）
   */
  function gridImagesHtml(urls) {
    var list = (urls || []).filter(function (u) { return !!u; });
    var n = list.length;
    if (n === 0) return '';
    if (n === 1) {
      return '<div class="grid-9 single"><img class="g-item" loading="lazy" alt="图片" src="' +
        avatarSrc(list[0]) + '" onerror="this.onerror=null;this.src=App.DEFAULT_AVATAR"></div>';
    }
    var cols = (n === 2 || n === 4) ? 2 : 3;
    var html = '<div class="grid-9 cols-' + cols + '">';
    list.forEach(function (u) {
      html += '<img class="g-item" loading="lazy" alt="图片" src="' + avatarSrc(u) +
        '" onerror="this.onerror=null;this.src=App.DEFAULT_AVATAR">';
    });
    return html + '</div>';
  }

  /**
   * 帖子卡片 HTML。
   * @param {Object} post PostDetailOut / Feed items 元素（字段见 api.md 接口15/16）
   * @param {Object} opts { showVisibility: 显示可见范围（接口15 有该字段）, deletable: 显示本人帖删除键 }
   */
  function postCardHtml(post, opts) {
    opts = opts || {};
    var p = post || {};
    var canDelete = !!opts.deletable && p.userId !== undefined && p.userId !== null &&
      currentViewer() && String(p.userId) === String(currentViewer());
    var html = '<div class="feed-card card" data-post-id="' + esc(p.postId) + '">' +
      '<div class="feed-head">' +
      '<img class="avatar" alt="头像" src="' + avatarSrc(p.avatar) +
      '" onerror="this.onerror=null;this.src=App.DEFAULT_AVATAR">' +
      '<div class="feed-head-main">' +
      '<div class="feed-name">' + esc(p.nickname) + '</div>' +
      '<div class="feed-time">' + esc(p.createTimeStr) +
      (opts.showVisibility && p.visibilityTypeStr ? ' · ' + esc(p.visibilityTypeStr) : '') +
      '</div>' +
      '</div>' +
      (canDelete ? '<button class="btn-link-danger btn-del-post" data-id="' + esc(p.postId) + '">删除</button>' : '') +
      '</div>';
    if (p.content) {
      html += '<div class="feed-content">' + esc(p.content) + '</div>';
    }
    html += gridImagesHtml(p.imageUrls);
    html += '</div>';
    return html;
  }

  /** 帖子删除（事件委托）：仅本人帖按钮可见；删除非幂等（1010），confirm + loading 防双击 */
  function bindPostDelete(container, onDone) {
    container.addEventListener('click', async function (ev) {
      var btn = ev.target.closest ? ev.target.closest('.btn-del-post') : null;
      if (!btn) return;
      var postId = btn.getAttribute('data-id');
      if (!window.confirm('确定删除这条朋友圈吗？（删除后不可恢复）')) return;
      var handler = withLoading(btn, '删除中', async function () {
        try {
          await api('/api/posts/' + encodeURIComponent(postId), { method: 'DELETE' });
          toast('已删除');
          if (onDone) onDone(postId);
        } catch (e) {
          /* api 已 toast */
        }
      });
      await handler();
    });
  }

  /** 空状态 HTML */
  function emptyHtml(text) {
    return '<div class="empty">' + esc(text) + '</div>';
  }

  /* ---------------- 导出 ---------------- */

  window.App = {
    APP_ID: APP_ID,
    VIEWER_COOKIE: VIEWER_COOKIE,
    DEFAULT_AVATAR: DEFAULT_AVATAR,
    api: api,
    uploadImage: uploadImage,
    toast: toast,
    esc: esc,
    getQueryParam: getQueryParam,
    currentViewer: currentViewer,
    setViewer: setViewer,
    needViewer: needViewer,
    withLoading: withLoading,
    mountHeader: mountHeader,
    apiUrl: apiUrl,
    assetUrl: assetUrl,
    avatarSrc: avatarSrc,
    gridImagesHtml: gridImagesHtml,
    postCardHtml: postCardHtml,
    bindPostDelete: bindPostDelete,
    emptyHtml: emptyHtml
  };
})();
