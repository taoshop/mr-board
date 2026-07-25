// e2e/login.spec.js
// Playwright / Cypress 端到端测试示例：登录流程

describe('登录流程', () => {
  beforeEach(() => {
    cy.visit('/login')
  })

  it('应显示登录页面', () => {
    cy.contains('登录')
  })

  it('错误密码应提示失败', () => {
    cy.get('input[name="username"]').type('admin')
    cy.get('input[name="password"]').type('wrong')
    cy.get('button[type="submit"]').click()
    cy.contains('用户名或密码错误')
  })
})
