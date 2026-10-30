local function emit(t) localised_print('PUMP11 '..game.table_to_json(t)) end
local function ent(e) if not e or not e.valid then return nil end return {name=e.name,type=e.type,unit=e.unit_number,position=e.position,direction=e.direction} end
local function fluid(e) if not e or not e.valid then return nil end return e.fluidbox[1] end
local function state()
 local t=global.loco.train;return {state=t.state,manual=t.manual_mode,speed=t.speed,station=ent(t.station),loco=ent(global.loco),wagon=ent(global.wagon),orientation=global.wagon.orientation,wagon_fluid=fluid(global.wagon)}
end
local function links(p)
 local out={};for _,c in ipairs(p.fluidbox.get_pipe_connections(1)) do out[#out+1]={flow=c.flow_direction,position=c.position,target_position=c.target_position,target=c.target and ent(c.target.owner)} end;return out
end
local function snapshot(tick)
 local r=global.rig;return {kind='trace',tick=tick,phase=global.phase,created_tick=global.start,elapsed=tick-global.start,train=state(),pump=ent(r.pump),pump_energy=r.pump.energy,pump_status=r.pump.status,pump_active=r.pump.active,pump_fluid=fluid(r.pump),source=ent(r.source),source_fluid=fluid(r.source),pump_network=r.pump.electric_network_id,power_network=r.power.electric_network_id,pump_rail_target=ent(r.pump.pump_rail_target),links=links(r.pump)}
end
local function phase_start(phase,tick)
 if global.rig then for _,e in ipairs({global.rig.pump,global.rig.source,global.rig.pole,global.rig.power}) do if e.valid then e.destroy() end end end
 global.wagon.fluidbox[1]=nil;global.phase=phase;global.start=tick;global.first_power=nil;global.first_flow=nil
 local name=({'pump','oracle-pump-fast','oracle-pump-slow'})[phase];local pos=global.wagon.position
 local p=global.surface.create_entity{name=name,position={pos.x-2,pos.y-2},direction=defines.direction.east,force='player'}
 local z=p.position
 local src=global.surface.create_entity{name='oracle-fluid-source',position={z.x-2,z.y},force='player'}
 local pole=global.surface.create_entity{name='oracle-isolated-pole',position=z,force='player'}
 local power=global.surface.create_entity{name='electric-energy-interface',position=z,force='player'}
 if not p or not src or not pole or not power then emit({kind='rig_error',phase=phase,tick=tick});global.phase=99;return end
 power.power_production=1000000;src.fluidbox[1]={name='water',amount=5000,temperature=15};global.rig={pump=p,source=src,pole=pole,power=power}
 emit({kind='phase_start',phase=phase,tick=tick,name=name,connector_speed_declared=({1/64,1/32,1/128})[phase],vanilla_speed_from_schema=(phase==1),pumping_speed_actual=p.prototype.pumping_speed,controlled_reset='fresh pump and source; wagon fluid cleared at boundary',train=state(),source_capacity=src.fluidbox.get_capacity(1),pump_capacity=p.fluidbox.get_capacity(1),links=links(p)})
 emit(snapshot(tick))
end
script.on_init(function()
 local s=game.create_surface('pump11',{seed=12345,water='none',starting_area='none',autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}});global.surface=s
 for y=-400,96,32 do s.request_to_generate_chunks({0,y},1) end;s.force_generate_chunk_requests()
 local n=0;for y=-350,60,2 do if s.create_entity{name='straight-rail',position={0,y},direction=defines.direction.north,force='player'} then n=n+1 end end
 local l=s.create_entity{name='locomotive',position={0,0},direction=defines.direction.north,force='player'}
 local w=s.create_entity{name='fluid-wagon',position={0,7},direction=defines.direction.north,force='player'}
 local st=s.create_entity{name='train-stop',position={2,-300},direction=defines.direction.north,force='player'}
 if not l or not w or not st then emit({kind='setup_error',loco=l~=nil,wagon=w~=nil,station=st~=nil,rails=n});return end
 st.backer_name='Pump11';l.get_fuel_inventory().insert{name='coal',count=30};global.loco=l;global.wagon=w;global.station=st;global.phase=0
 emit({kind='setup',rails=n,loco=ent(l),wagon=ent(w),station=ent(st),station_rail=ent(st.connected_rail),weight=l.train.weight,car_count=#l.train.carriages})
end)
script.on_event(defines.events.on_tick,function(e)
 if not global.loco then return end
 if e.tick==0 then local t=global.loco.train;t.schedule={current=1,records={{station='Pump11',wait_conditions={{type='time',compare_type='and',ticks=12000}}}}};t.manual_mode=false end
 if global.phase==0 then
  if e.tick%60==0 then emit({kind='arrival_trace',tick=e.tick,train=state()}) end
  if global.loco.train.state==defines.train_state.wait_station and global.loco.train.station and global.loco.train.speed==0 then emit({kind='native_arrival',tick=e.tick,train=state()});phase_start(1,e.tick) end
  if e.tick==1000 and global.phase==0 then emit({kind='arrival_failed',tick=e.tick,train=state()});global.phase=99 end
 elseif global.phase<99 then
  local dt=e.tick-global.start;local r=global.rig
  if not global.first_power and r.pump.energy>0 then global.first_power=e.tick;emit({kind='first_power',phase=global.phase,tick=e.tick,elapsed=dt,energy=r.pump.energy}) end
  local f=fluid(global.wagon)
  if not global.first_flow and f and f.amount>0 then global.first_flow=e.tick;emit({kind='first_flow',phase=global.phase,tick=e.tick,elapsed=dt,wagon_fluid=f}) end
  emit(snapshot(e.tick))
  if dt>=240 then emit({kind='phase_end',phase=global.phase,tick=e.tick,created=global.start,first_power=global.first_power,first_flow=global.first_flow,train=state()});if global.phase==3 then global.phase=99;emit({kind='done',tick=e.tick}) else phase_start(global.phase+1,e.tick) end end
 end
end)
