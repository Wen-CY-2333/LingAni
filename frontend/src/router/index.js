import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import CategoryView from '../views/CategoryView.vue'
import RankView from '../views/RankView.vue'
import ScheduleView from '../views/ScheduleView.vue'
import SearchView from '../views/SearchView.vue'
import AnimeDetailView from '../views/AnimeDetailView.vue'
import PlayView from '../views/PlayView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import UserView from '../views/UserView.vue'
import UserFavoritesView from '../views/UserFavoritesView.vue'
import UserHistoryView from '../views/UserHistoryView.vue'
import AdminLayout from '../views/admin/AdminLayout.vue'
import AdminDashboardView from '../views/admin/AdminDashboardView.vue'
import AdminUsersView from '../views/admin/AdminUsersView.vue'
import AdminContentView from '../views/admin/AdminContentView.vue'
import AdminRecordsView from '../views/admin/AdminRecordsView.vue'
import { authState, isLoggedIn } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/category', name: 'category', component: CategoryView },
    { path: '/rank', name: 'rank', component: RankView },
    { path: '/schedule', name: 'schedule', component: ScheduleView },
    { path: '/search', name: 'search', component: SearchView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/anime/:id', name: 'anime-detail', component: AnimeDetailView },
    { path: '/anime/:videoId/play/:sectionId', name: 'anime-play', component: PlayView },
    { path: '/user', name: 'user', component: UserView, meta: { requiresAuth: true } },
    { path: '/user/favorites', name: 'user-favorites', component: UserFavoritesView, meta: { requiresAuth: true } },
    { path: '/user/history', name: 'user-history', component: UserHistoryView, meta: { requiresAuth: true } },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', name: 'admin-dashboard', component: AdminDashboardView },
        { path: 'users', name: 'admin-users', component: AdminUsersView },
        { path: 'content', name: 'admin-content', component: AdminContentView },
        { path: 'records', name: 'admin-records', component: AdminRecordsView }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior(to) {
    if (to.hash) {
      return { el: to.hash, top: 72, behavior: 'smooth' }
    }
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isLoggedIn.value) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && authState.user?.role !== 1) {
    return { path: '/' }
  }
  return true
})

export default router
