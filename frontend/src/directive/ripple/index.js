export default {
  mounted(el) {
    el.style.position = 'relative';
    el.style.overflow = 'hidden';

    el.addEventListener('click', function(e) {
      const rect = el.getBoundingClientRect();
      const circle = document.createElement('span');
      const diameter = Math.max(el.clientWidth, el.clientHeight);
      const radius = diameter / 2;

      circle.style.width = circle.style.height = `${diameter}px`;
      circle.style.left = `${e.clientX - rect.left - radius}px`;
      circle.style.top = `${e.clientY - rect.top - radius}px`;
      circle.style.position = 'absolute';
      circle.style.borderRadius = '50%';
      circle.style.backgroundColor = 'rgba(255, 255, 255, 0.4)';
      circle.style.transform = 'scale(0)';
      circle.style.animation = 'glass-ripple 600ms linear';
      circle.style.pointerEvents = 'none';

      const ripple = el.querySelector('.glass-ripple-effect');
      if (ripple) {
        ripple.remove();
      }

      circle.classList.add('glass-ripple-effect');
      el.appendChild(circle);

      setTimeout(() => {
        circle.remove();
      }, 600);
    });
  }
}
