<script setup>
const year = new Date().getFullYear()
</script>

<template>
  <footer class="app-footer">
    <div class="container footer-inner">
      <div class="footer-brand">
        <div class="footer-logo">
          <span class="logo-text">灵漫 LingAni</span>
        </div>
        <p class="footer-desc">让每一次追番，都有智能相伴。</p>
      </div>
      <div class="footer-links">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/category">分类</RouterLink>
        <RouterLink to="/schedule">追番周表</RouterLink>
        <RouterLink to="/rank">排行榜</RouterLink>
        <RouterLink to="/settings/mascot">看板娘设置</RouterLink>
      </div>
    </div>
    <div class="footer-bottom">
      <div class="container">© {{ year }} 灵漫 LingAni</div>
    </div>
  </footer>
</template>

<style scoped lang="scss">
.app-footer {
  margin-top: 56px;
  border-top: 1px solid hsl(var(--border));
  background-color: hsl(var(--background));
}

.footer-inner {
  display: grid;
  gap: 24px;
  padding-top: 32px;
  padding-bottom: 32px;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.footer-logo {
  display: flex;
  align-items: center;
}

.logo-text {
  font-family: 'LingAni Display', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 20px;
  font-weight: 400;
  letter-spacing: 1px;
  color: hsl(var(--brand));
}

.footer-desc {
  margin-top: 10px;
  font-size: 13.5px;
  color: hsl(var(--muted-foreground));
}

.footer-links {
  display: flex;
  flex-direction: row;
  align-items: center;
  align-self: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px 22px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));

  a:hover {
    color: hsl(var(--brand));
  }
}

@media (max-width: 768px) {
  .footer-links {
    justify-content: flex-start;
    align-self: start;
  }
}

.footer-bottom {
  padding: 14px 0 20px;
  border-top: 1px solid hsl(var(--border) / 0.6);
  font-size: 12px;
  color: hsl(var(--muted-foreground) / 0.8);
}
</style>
