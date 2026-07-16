const moneyFormatter = new Intl.NumberFormat("zh-TW", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

const integerFormatter = new Intl.NumberFormat("zh-TW", {
  maximumFractionDigits: 0
});

export function formatPrice(value) {
  return moneyFormatter.format(Number(value || 0));
}

export function formatMoney(value) {
  return `$${moneyFormatter.format(Number(value || 0))}`;
}

export function formatQty(value) {
  return integerFormatter.format(Number(value || 0));
}

export function formatPercent(value) {
  const number = Number(value || 0);
  return `${number >= 0 ? "+" : ""}${number.toFixed(2)}%`;
}

export function formatSignedMoney(value) {
  const number = Number(value || 0);
  return `${number >= 0 ? "+" : ""}${moneyFormatter.format(number)}`;
}

export function formatDateTime(value) {
  if (!value) return "--";
  return new Date(value).toLocaleString("zh-TW");
}

export function formatTime(value) {
  if (!value) return "--";
  return new Date(value).toLocaleTimeString("zh-TW", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  });
}

export function sideLabel(value) {
  return value === "BUY" ? "買進" : "賣出";
}

export function sideClass(value) {
  return value === "BUY" ? "positive" : "negative";
}

export function typeLabel(value) {
  if (value === "MARKET") return "市價";
  if (value === "FOK") return "FOK";
  return "限價";
}

export function statusLabel(value) {
  if (value === "PENDING") return "委託中";
  if (value === "PARTIAL") return "部分成交";
  if (value === "FILLED") return "全部成交";
  if (value === "CANCELLED") return "已取消";
  return value || "--";
}

export function roleLabel(value) {
  if (value === "ADMIN") return "管理員";
  if (value === "PLAYER") return "玩家";
  return value || "--";
}
