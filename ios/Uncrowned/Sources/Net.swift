import Foundation

/* ---------- rede: pedidos simples com tempo máximo e o mesmo "navegador" que o app de PC usa ---------- */
enum Net {
    static let ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36"
    static let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 15
        c.timeoutIntervalForResource = 30
        c.httpAdditionalHeaders = ["User-Agent": ua, "Accept-Language": "pt-BR,pt;q=0.9,en;q=0.8"]
        c.urlCache = URLCache(memoryCapacity: 8 << 20, diskCapacity: 64 << 20)
        return URLSession(configuration: c)
    }()

    struct HTTPError: Error, LocalizedError { let code: Int; var errorDescription: String? { "HTTP \(code)" } }

    static func data(_ url: String, method: String = "GET", headers: [String: String] = [:], body: Data? = nil) async throws -> Data {
        guard let u = URL(string: url) else { throw HTTPError(code: 0) }
        var r = URLRequest(url: u); r.httpMethod = method; r.httpBody = body
        for (k, v) in headers { r.setValue(v, forHTTPHeaderField: k) }
        let (d, resp) = try await session.data(for: r)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if !(200..<300).contains(code) { throw HTTPError(code: code) }
        return d
    }
    static func text(_ url: String, headers: [String: String] = [:]) async throws -> String {
        String(decoding: try await data(url, headers: headers), as: UTF8.self)
    }
    static func json(_ url: String, method: String = "GET", headers: [String: String] = [:], body: Any? = nil) async throws -> Any {
        var h = headers
        var b: Data? = nil
        if let body { b = try JSONSerialization.data(withJSONObject: body); h["Content-Type"] = "application/json" }
        return try JSONSerialization.jsonObject(with: try await data(url, method: method, headers: h, body: b), options: [.fragmentsAllowed])
    }
    static func enc(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? s }
}

/* ---------- ajudantes pra JSON solto (o YouTube manda tudo muito aninhado) ---------- */
typealias JObj = [String: Any]
extension Dictionary where Key == String, Value == Any {
    func o(_ k: String) -> JObj? { self[k] as? JObj }
    func a(_ k: String) -> [Any] { self[k] as? [Any] ?? [] }
    func s(_ k: String) -> String { if let v = self[k] as? String { return v }; if let v = self[k] as? NSNumber { return v.stringValue }; return "" }
    func i(_ k: String) -> Int { (self[k] as? NSNumber)?.intValue ?? Int(s(k)) ?? 0 }
    func d(_ k: String) -> Double { (self[k] as? NSNumber)?.doubleValue ?? Double(s(k)) ?? 0 }
    func b(_ k: String) -> Bool { (self[k] as? NSNumber)?.boolValue ?? false }
}

/* procura uma chave em qualquer profundidade */
func findKey(_ x: Any?, _ key: String, depth: Int = 0) -> Any? {
    if depth > 16 { return nil }
    if let o = x as? JObj {
        if let v = o[key] { return v }
        for v in o.values { if let r = findKey(v, key, depth: depth + 1) { return r } }
    } else if let a = x as? [Any] {
        for v in a { if let r = findKey(v, key, depth: depth + 1) { return r } }
    }
    return nil
}

/* texto do YouTube: { runs: [{text}] } ou { simpleText } */
func runsText(_ x: Any?) -> String {
    guard let o = x as? JObj else { return "" }
    let r = o.a("runs")
    if !r.isEmpty { return r.compactMap { ($0 as? JObj)?.s("text") }.joined() }
    return o.s("simpleText")
}

/* corta um objeto JSON que começa em "start" dentro de um texto (ytInitialData) */
func jsonAt(_ s: String, from start: String.Index) -> String? {
    var depth = 0, inStr = false, esc = false
    var i = start
    while i < s.endIndex {
        let ch = s[i]
        if inStr { if esc { esc = false } else if ch == "\\" { esc = true } else if ch == "\"" { inStr = false } }
        else if ch == "\"" { inStr = true }
        else if ch == "{" { depth += 1 }
        else if ch == "}" { depth -= 1; if depth == 0 { return String(s[start...i]) } }
        i = s.index(after: i)
    }
    return nil
}

/* normaliza pra comparar nomes: sem acento, minúsculo, só letras e números */
func norm(_ x: String) -> String {
    let f = x.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pt_BR")).lowercased()
    let noPar = f.replacingOccurrences(of: "\\(.*?\\)|\\[.*?\\]", with: " ", options: .regularExpression)
    return noPar.replacingOccurrences(of: "[^a-z0-9]+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces)
}
func cleanArtist(_ a: String) -> String {
    var s = a.replacingOccurrences(of: "(?i)\\s*-\\s*topic$", with: "", options: .regularExpression)
    s = s.replacingOccurrences(of: "(?i)vevo$", with: "", options: .regularExpression)
    s = s.replacingOccurrences(of: "(?i)\\s*\\(?(official|oficial)\\)?\\s*$", with: "", options: .regularExpression)
    let parts = s.components(separatedBy: CharacterSet(charactersIn: ",&/"))
    var first = parts.first ?? s
    for sep in [" feat.", " feat ", " ft.", " ft ", " x "] { if let r = first.range(of: sep, options: .caseInsensitive) { first = String(first[..<r.lowerBound]) } }
    return first.trimmingCharacters(in: .whitespaces)
}
func durText(_ s: String) -> Int { // "3:25" -> ms
    let p = s.split(separator: ":").compactMap { Int($0) }
    guard !p.isEmpty, s.range(of: "^\\d{1,2}(:\\d{2}){1,2}$", options: .regularExpression) != nil else { return 0 }
    return p.reduce(0) { $0 * 60 + $1 } * 1000
}
func fmtTime(_ sec: Double) -> String {
    guard sec.isFinite, sec >= 0 else { return "0:00" }
    let s = Int(sec); return String(format: "%d:%02d", s / 60, s % 60)
}

/* espera várias tarefas com no máximo N ao mesmo tempo */
func pool<T, R>(_ items: [T], _ n: Int, _ f: @escaping (T) async -> R?) async -> [R?] {
    var out = [R?](repeating: nil, count: items.count)
    await withTaskGroup(of: (Int, R?).self) { g in
        var next = 0
        for _ in 0..<min(n, items.count) { let k = next; next += 1; g.addTask { (k, await f(items[k])) } }
        while let (k, r) = await g.next() {
            out[k] = r
            if next < items.count { let j = next; next += 1; g.addTask { (j, await f(items[j])) } }
        }
    }
    return out
}
