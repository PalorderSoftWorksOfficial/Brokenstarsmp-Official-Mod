local function cap(s)
  return (s:gsub("^%l", string.upper))
end

local wrap

local function auto(v)
  if type(v) == "userdata" then
    return wrap(v)
  end
  return v
end

function wrap(value)
  return setmetatable({}, {
    __index = function(_, key)
      local direct = value[key]
      if direct ~= nil then
        if type(direct) == "function" then
          return function(_, ...)
            return auto(direct(value, ...))
          end
        end
        return auto(direct)
      end
      local getter = value["get" .. cap(key)]
      if type(getter) == "function" then
        return auto(getter(value))
      end
      local isGetter = value["is" .. cap(key)]
      if type(isGetter) == "function" then
        return auto(isGetter(value))
      end
      return nil
    end,
    __newindex = function(_, key, newValue)
      local setter = value["set" .. cap(key)]
      if type(setter) == "function" then
        setter(value, newValue)
        return
      end
      rawset(_, key, newValue)
    end,
    __call = function(_, ...)
      return auto(value(...))
    end
  })
end

local function makeNamespace(path)
  return setmetatable({}, {
    __index = function(self, key)
      local full = path == "" and key or path .. "." .. key
      local ok, cls = pcall(java.import, full)
      if ok and cls ~= nil then
        local proxy = setmetatable({}, {
          __index = function(_, member)
            local v = cls[member]
            if v ~= nil then
              if type(v) == "function" then
                return function(_, ...)
                  local ok1, res1 = pcall(v, ...)
                  if ok1 then return auto(res1) end
                  local ok2, res2 = pcall(v, cls, ...)
                  if ok2 then return auto(res2) end
                  error(res2)
                end
              end
              return auto(v)
            end
            return nil
          end,
          __call = function(_, ...)
            return auto(cls(...))
          end
        })
        rawset(self, key, proxy)
        return proxy
      end
      local ns = makeNamespace(full)
      rawset(self, key, ns)
      return ns
    end
  })
end

_globalPackages = makeNamespace("")
setmetatable(_G, {
  __index = function(_, key)
    return _globalPackages[key]
  end
})

exports = {}

function export(name, value)
  exports[name] = value
  return value
end

function fetch(name)
  return exports[name]
end

luajava = {
  newInstance = function(className, ...)
    return auto(java.import(className)(...))
  end,
  bindClass = java.import,
  new = java.new,
  createProxy = java.proxy,
  loadLib = function(className, methodName)
    return java.loadlib(className, methodName)()
  end,
  unwrap = java.unwrap
}