/*
 * lx-music user-api 宿主骨架（WebView 引擎专用）。
 *
 * 在 WebView 内执行用户/预设 .js 音源脚本（grass / juhe / 第三方导入）。
 * 脚本解构 globalThis.lx 取得 on/send/request/utils/EVENT_NAMES 等；
 * 本文件提供这些与 Kotlin 侧 nativeBridge（addJavascriptInterface 暴露）的桥接。
 *
 * 协议（对齐 lx-music 桌面/mobile）：
 *  - 脚本 load 后调用 lx.send(EVENT_NAMES.init, {sources}) 上报支持音源（宿主记为 'inited'）。
 *  - 宿主需要取流/搜索时调用脚本通过 lx.on('request') 注册的 handler，并 await 其返回值。
 *  - 脚本内部用 lx.request(url, options, cb) 发起 HTTP，cb(err, response) 由 native 异步回传。
 */
(function () {
  // Android TV 的旧 WebView（Chrome 66 等）没有 globalThis。
  // 同时不能直接读取同名局部变量判断 nativeBridge，否则会被 var 提升成 undefined。
  var __global =
    typeof window !== "undefined"
      ? window
      : typeof globalThis !== "undefined"
        ? globalThis
        : this;
  if (__global && !__global.globalThis) __global.globalThis = __global;
  var nativeBridge =
    __global && __global.nativeBridge ? __global.nativeBridge : null;

  var __handlers = {};
  var __reqId = 0;
  var __pending = {};

  // ---- 简易 MD5（纯 JS，供 utils.toMD5；脚本取流通常不依赖，仅兜底） ----
  function md5(s) {
    function safeAdd(x, y) {
      var lsw = (x & 0xffff) + (y & 0xffff);
      var msw = (x >> 16) + (y >> 16) + (lsw >> 16);
      return (msw << 16) | (lsw & 0xffff);
    }
    function rol(num, cnt) {
      return (num << cnt) | (num >>> (32 - cnt));
    }
    function cmn(q, a, b, x, s, t) {
      return safeAdd(rol(safeAdd(safeAdd(a, q), safeAdd(x, t)), s), b);
    }
    function ff(a, b, c, d, x, s, t) {
      return cmn((b & c) | (~b & d), a, b, x, s, t);
    }
    function gg(a, b, c, d, x, s, t) {
      return cmn((b & d) | (c & ~d), a, b, x, s, t);
    }
    function hh(a, b, c, d, x, s, t) {
      return cmn(b ^ c ^ d, a, b, x, s, t);
    }
    function ii(a, b, c, d, x, s, t) {
      return cmn(c ^ (b | ~d), a, b, x, s, t);
    }
    function md5cycle(x, k) {
      var a = x[0], b = x[1], c = x[2], d = x[3];
      a = ff(a, b, c, d, k[0], 7, -680876936);
      d = ff(d, a, b, c, k[1], 12, -389564586);
      c = ff(c, d, a, b, k[2], 17, 606105819);
      b = ff(b, c, d, a, k[3], 22, -1044525330);
      a = ff(a, b, c, d, k[4], 7, -176418897);
      d = ff(d, a, b, c, k[5], 12, 1200080426);
      c = ff(c, d, a, b, k[6], 17, -1473231341);
      b = ff(b, c, d, a, k[7], 22, -45705983);
      a = ff(a, b, c, d, k[8], 7, 1770035416);
      d = ff(d, a, b, c, k[9], 12, -1958414417);
      c = ff(c, d, a, b, k[10], 17, -42063);
      b = ff(b, c, d, a, k[11], 22, -1990404162);
      a = ff(a, b, c, d, k[12], 7, 1804603682);
      d = ff(d, a, b, c, k[13], 12, -40341101);
      c = ff(c, d, a, b, k[14], 17, -1502002290);
      b = ff(b, c, d, a, k[15], 22, 1236535329);
      a = gg(a, b, c, d, k[1], 5, -165796510);
      d = gg(d, a, b, c, k[6], 9, -1069501632);
      c = gg(c, d, a, b, k[11], 14, 643717713);
      b = gg(b, c, d, a, k[0], 20, -373897302);
      a = gg(a, b, c, d, k[5], 5, -701558691);
      d = gg(d, a, b, c, k[10], 9, 38016083);
      c = gg(c, d, a, b, k[15], 14, -660478335);
      b = gg(b, c, d, a, k[4], 20, -405537848);
      a = gg(a, b, c, d, k[9], 5, 568446438);
      d = gg(d, a, b, c, k[14], 9, -1019803690);
      c = gg(c, d, a, b, k[3], 14, -187363961);
      b = gg(b, c, d, a, k[8], 20, 1163531501);
      a = gg(a, b, c, d, k[13], 5, -1444681467);
      d = gg(d, a, b, c, k[2], 9, -51403784);
      c = gg(c, d, a, b, k[7], 14, 1735328473);
      b = gg(b, c, d, a, k[12], 20, -1926607734);
      a = hh(a, b, c, d, k[5], 4, -378558);
      d = hh(d, a, b, c, k[8], 11, -2022574463);
      c = hh(c, d, a, b, k[11], 16, 1839030562);
      b = hh(b, c, d, a, k[14], 23, -35309556);
      a = hh(a, b, c, d, k[1], 4, -1530992060);
      d = hh(d, a, b, c, k[4], 11, 1272893353);
      c = hh(c, d, a, b, k[7], 16, -155497632);
      b = hh(b, c, d, a, k[10], 23, -1094730640);
      a = hh(a, b, c, d, k[13], 4, 681279174);
      d = hh(d, a, b, c, k[0], 11, -358537222);
      c = hh(c, d, a, b, k[3], 16, -722521979);
      b = hh(b, c, d, a, k[6], 23, 76029189);
      a = hh(a, b, c, d, k[9], 4, -640364487);
      d = hh(d, a, b, c, k[12], 11, -421815835);
      c = hh(c, d, a, b, k[15], 16, 530742520);
      b = hh(b, c, d, a, k[2], 23, -995338651);
      a = ii(a, b, c, d, k[0], 6, -198630844);
      d = ii(d, a, b, c, k[7], 10, 1126891415);
      c = ii(c, d, a, b, k[14], 15, -1416354905);
      b = ii(b, c, d, a, k[5], 21, -57434055);
      a = ii(a, b, c, d, k[12], 6, 1700485571);
      d = ii(d, a, b, c, k[3], 10, -1894986606);
      c = ii(c, d, a, b, k[10], 15, -1051523);
      b = ii(b, c, d, a, k[1], 21, -2054922799);
      a = ii(a, b, c, d, k[8], 6, 1873313359);
      d = ii(d, a, b, c, k[15], 10, -30611744);
      c = ii(c, d, a, b, k[6], 15, -1560198380);
      b = ii(b, c, d, a, k[13], 21, 1309151649);
      a = ii(a, b, c, d, k[4], 6, -145523070);
      d = ii(d, a, b, c, k[11], 10, -1120210379);
      c = ii(c, d, a, b, k[2], 15, 718787259);
      b = ii(b, c, d, a, k[9], 21, -343485551);
      x[0] = safeAdd(a, x[0]);
      x[1] = safeAdd(b, x[1]);
      x[2] = safeAdd(c, x[2]);
      x[3] = safeAdd(d, x[3]);
    }
    function md51(s) {
      var n = s.length,
        state = [1732584193, -271733879, -1732584194, 271733878],
        i;
      for (i = 64; i <= s.length; i += 64) {
        md5cycle(state, md5blk(s.substring(i - 64, i)));
      }
      s = s.substring(i - 64);
      var tail = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
      for (i = 0; i < s.length; i += 1) tail[i >> 2] |= s.charCodeAt(i) << ((i % 4) << 3);
      tail[i >> 2] |= 0x80 << ((i % 4) << 3);
      if (i > 55) {
        md5cycle(state, tail);
        for (i = 0; i < 16; i += 1) tail[i] = 0;
      }
      tail[14] = n * 8;
      md5cycle(state, tail);
      return state;
    }
    function md5blk(s) {
      var md5blks = [],
        i;
      for (i = 0; i < 64; i += 4) {
        md5blks[i >> 2] =
          s.charCodeAt(i) +
          (s.charCodeAt(i + 1) << 8) +
          (s.charCodeAt(i + 2) << 16) +
          (s.charCodeAt(i + 3) << 24);
      }
      return md5blks;
    }
    function rhex(n) {
      var hexChr = "0123456789abcdef".split("");
      var s = "",
        j = 0;
      for (; j < 4; j += 1)
        s += hexChr[(n >> (j * 8 + 4)) & 0x0f] + hexChr[(n >> (j * 8)) & 0x0f];
      return s;
    }
    function hex(x) {
      var out = "",
        i;
      for (i = 0; i < x.length; i += 1) out += rhex(x[i]);
      return out;
    }
    return hex(md51(s));
  }

  var lx = {
    env: "android",
    version: "1.0.0",
    currentScriptInfo: {},
    EVENT_NAMES: {
      request: "request",
      init: "inited",
      ready: "ready",
      config: "config",
      status: "status",
      updateAlert: "updateAlert",
    },
    utils: {
      toMD5: function (t) {
        return md5(String(t));
      },
      toBase64: function (t) {
        try {
          return btoa(unescape(encodeURIComponent(String(t))));
        } catch (e) {
          return "";
        }
      },
      toHex: function (bytes) {
        if (typeof bytes === "string") return md5(bytes);
        try {
          return Array.prototype.map
            .call(bytes, function (x) {
              return ("00" + (x & 0xff).toString(16)).slice(-2);
            })
            .join("");
        } catch (e) {
          return "";
        }
      },
      toStringify: function (o) {
        return JSON.stringify(o);
      },
      toData: function (t) {
        try {
          return JSON.parse(t);
        } catch (e) {
          return null;
        }
      },
      getRawText: function (t) {
        return t;
      },
    },

    on: function (name, fn) {
      (__handlers[name] = __handlers[name] || []).push(fn);
      return this;
    },

    send: function (name, data) {
      if (nativeBridge) {
        nativeBridge.onScriptSend(name, JSON.stringify(data === undefined ? null : data));
      }
      return this;
    },

    // 宿主触发 request 事件：调用脚本 lx.on('request') 注册的 handler 并 await 其返回值。
    __emitRequest: function (reqId, dataJson) {
      var data;
      try {
        data = JSON.parse(dataJson);
      } catch (e) {
        data = {};
      }
      var hs = __handlers["request"] || [];
      if (!hs.length) {
        if (nativeBridge)
          nativeBridge.onRequestResult(reqId, JSON.stringify("未注册 request 处理器"), null);
        return;
      }
      Promise.resolve()
        .then(function () {
          return hs[0](data);
        })
        .then(function (res) {
          if (nativeBridge)
            nativeBridge.onRequestResult(
              reqId,
              null,
              JSON.stringify(res === undefined ? null : res)
            );
        })
        .catch(function (e) {
          if (nativeBridge)
            nativeBridge.onRequestResult(
              reqId,
              JSON.stringify(String((e && e.message) || e)),
              null
            );
        });
    },

    // 脚本内 lx.request(url, options, cb)
    request: function (url, options, cb) {
      var id = ++__reqId;
      __pending[id] = cb;
      var opts = options || {};
      if (nativeBridge) {
        nativeBridge.request(
          id,
          String(url),
          JSON.stringify({
            method: opts.method || "GET",
            headers: opts.headers || {},
            body: opts.body != null ? String(opts.body) : null,
            form: opts.form || null,
          })
        );
      }
    },

    __resolveRequest: function (id, errJson, respJson) {
      var cb = __pending[id];
      if (!cb) return;
      delete __pending[id];
      var err = null,
        resp = null;
      try {
        err = errJson ? JSON.parse(errJson) : null;
      } catch (e) {}
      try {
        resp = respJson ? JSON.parse(respJson) : null;
      } catch (e) {}
      cb(err, resp);
    },
  };

  __global.lx = lx;
  __global.nativeBridge = nativeBridge;
})();
