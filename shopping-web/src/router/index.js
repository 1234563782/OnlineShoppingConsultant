import { createRouter, createWebHistory } from 'vue-router'
import { fetchMe } from '../api/http'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import ChatView from '../views/ChatView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/login', component: LoginView, meta: { guest: true } },
    { path: '/register', component: RegisterView, meta: { guest: true } },
    { path: '/chat', component: ChatView, meta: { requiresAuth: true } }
  ]
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth && !to.meta.guest) {
    return true
  }
  try {
    await fetchMe()
    if (to.meta.guest) {
      return '/chat'
    }
    return true
  } catch {
    if (to.meta.requiresAuth) {
      return '/login'
    }
    return true
  }
})

export default router
