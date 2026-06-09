import axios from 'axios'

const http = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' }
})

export async function fetchMe() {
  const { data } = await http.get('/auth/me')
  return data
}

export async function login(username, password) {
  const { data } = await http.post('/auth/login', { username, password })
  return data
}

export async function register(username, password, displayName) {
  const { data } = await http.post('/auth/register', { username, password, displayName })
  return data
}

export async function logout() {
  await http.post('/auth/logout')
}

export { http }
