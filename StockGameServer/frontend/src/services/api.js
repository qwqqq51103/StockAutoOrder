const API_BASE = (import.meta.env.VITE_API_BASE || "").replace(/\/$/, "");

function buildUrl(path) {
  return `${API_BASE}${path}`;
}

async function parseResponse(response) {
  const contentType = response.headers.get("content-type") || "";

  if (contentType.includes("application/json")) {
    return response.json();
  }

  return response.text();
}

async function request(path, { method = "GET", body, token } = {}) {
  const response = await fetch(buildUrl(path), {
    method,
    headers: {
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  });

  const payload = await parseResponse(response).catch(() => null);

  if (!response.ok) {
    const message = payload?.message || payload?.error || `${response.status} ${response.statusText}`;
    throw new Error(message);
  }

  return payload;
}

export function apiGet(path, token) {
  return request(path, { method: "GET", token });
}

export function apiPost(path, body, token) {
  return request(path, { method: "POST", body, token });
}

export function apiDelete(path, token) {
  return request(path, { method: "DELETE", token });
}

export function buildSocketUrl(path) {
  return buildUrl(path);
}
