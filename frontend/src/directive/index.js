import hasRole from './permission/hasRole'
import hasPermi from './permission/hasPermi'
import copyText from './common/copyText'
import track from './common/track'
import safeHtml from './safe-html'
import ripple from './ripple'

export default function directive(app){
  app.directive('hasRole', hasRole)
  app.directive('hasPermi', hasPermi)
  app.directive('copyText', copyText)
  app.directive('track', track)
  app.directive('safe-html', safeHtml)
  app.directive('ripple', ripple)
}