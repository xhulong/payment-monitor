interface PublicSeoOptions {
  title: string;
  description: string;
}

function upsertMeta(selector: string, attributes: Record<string, string>, content: string) {
  let element = document.head.querySelector<HTMLMetaElement>(selector);
  if (!element) {
    element = document.createElement('meta');
    Object.entries(attributes).forEach(([key, value]) => element?.setAttribute(key, value));
    document.head.appendChild(element);
  }
  element.setAttribute('content', content);
  return element;
}

export function usePublicSeo(options: PublicSeoOptions) {
  onMounted(() => {
    document.title = options.title;
    upsertMeta('meta[name="description"]', { name: 'description' }, options.description);
    upsertMeta('meta[property="og:title"]', { property: 'og:title' }, options.title);
    upsertMeta(
      'meta[property="og:description"]',
      { property: 'og:description' },
      options.description
    );
    upsertMeta('meta[property="og:type"]', { property: 'og:type' }, 'website');
  });

  onBeforeUnmount(() => {
    document.title = import.meta.env.VITE_APP_TITLE;
    upsertMeta('meta[name="description"]', { name: 'description' }, '');
    upsertMeta('meta[property="og:title"]', { property: 'og:title' }, import.meta.env.VITE_APP_TITLE);
    upsertMeta(
      'meta[property="og:description"]',
      { property: 'og:description' },
      ''
    );
  });
}
