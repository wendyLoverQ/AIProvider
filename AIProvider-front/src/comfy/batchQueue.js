export const COMFY_QUEUE_LIMIT = 20;

export function countComfyQueue(queue) {
  return (Array.isArray(queue?.queue_running) ? queue.queue_running.length : 0) +
    (Array.isArray(queue?.queue_pending) ? queue.queue_pending.length : 0);
}

export function canSubmitToComfyQueue(queue) {
  return countComfyQueue(queue) < COMFY_QUEUE_LIMIT;
}
