import api from './client';
import type { InstanceResponse } from './types';

export async function getInstance(instanceId: string): Promise<InstanceResponse> {
  const { data } = await api.get<InstanceResponse>(`/instances/${encodeURIComponent(instanceId)}`);
  return data;
}
