// Client-side confirmation is only a mistake-prevention aid. Server rules or
// the trusted backend must independently authorize each privileged write.
export function changeAdminTarget(admin, target) {
  if (admin.target === target) return false;
  admin.target = target;
  admin.confirm = false;
  return true;
}
